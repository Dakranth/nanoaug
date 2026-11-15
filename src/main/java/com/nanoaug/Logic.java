package com.nanoaug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;
import java.util.function.Predicate;

@EventBusSubscriber(modid = NanoAug.MODID)
public class Logic {

    private static boolean chaining;

    // ================================================================ números do jogo
    /** Custo de energia por uso (augs ACTION). */
    public static float cost(Aug a, AugData d) {
        return switch (a) {
            case NANOBLADE -> 25f;
            case PEPS -> 30f;
            case REMOTE_HACK -> 15f;
            case TESLA -> 35f;
            case TYPHOON -> 60f;
            case ICARUS_DASH -> d.lvl(a) == 1 ? 20f : d.lvl(a) == 2 ? 12f : 18f;
            default -> 0f;
        };
    }

    static int cooldown(Aug a) {
        return switch (a) {
            case PEPS, REMOTE_HACK -> 40;
            case TESLA -> 100;
            case NANOBLADE -> 20;
            case TYPHOON -> 300;
            case ICARUS_DASH -> 30;
            default -> 0;
        };
    }

    /** Gasto de energia por tick (augs TOGGLE). Camuflagem e TITAN gastam muito mais se você se mexe. */
    static float drain(Aug a, AugData d, ServerPlayer p) {
        int l = d.lvl(a);
        boolean moving = p.getDeltaMovement().horizontalDistanceSqr() > 0.002;
        return switch (a) {
            case SMART_VISION -> l >= 4 ? 0.02f : l == 3 ? 0.045f : 0.06f;
            case CLOAK -> 0.15f * (l == 3 ? 0.45f : l == 2 ? 0.7f : 1f) * (moving ? 3f : 1f);
            case TITAN -> 0.3f * (l == 3 ? 0.4f : l == 2 ? 0.7f : 1f) * (moving ? 3f : 1f);
            default -> 0f;
        };
    }

    static int rechargeDelay(AugData d) { return new int[]{100, 60, 30}[Math.min(2, d.lvl(Aug.SARIF_DELAY))]; }
    static int regenDelay(AugData d) { return new int[]{100, 60, 30}[Math.min(2, d.lvl(Aug.REGEN_DELAY))]; }

    // ================================================================ passivos (atributos)
    private static void mod(ServerPlayer p, Holder<Attribute> attr, String key, double amt, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NanoAug.MODID, key);
        if (amt == 0) inst.removeModifier(id);
        else inst.addOrUpdateTransientModifier(new AttributeModifier(id, amt, op));
    }

    static void applyPassives(ServerPlayer p) {
        AugData d = AugData.get(p);
        double k = d.potency();
        boolean arm = d.has(Aug.ARM), leg = d.has(Aug.LEG), klip = d.has(Aug.KLIPSPRINGER);
        mod(p, Attributes.ATTACK_DAMAGE, "arm_dmg", arm ? 2 * k : 0, AttributeModifier.Operation.ADD_VALUE);
        mod(p, Attributes.BLOCK_BREAK_SPEED, "arm_mine", arm ? 0.5 * k : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mod(p, Attributes.ATTACK_SPEED, "qs_atk", d.has(Aug.QUICKSILVER) ? 1.0 * k : 0, AttributeModifier.Operation.ADD_VALUE);
        mod(p, Attributes.MOVEMENT_SPEED, "leg_speed", leg ? 0.12 * k : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mod(p, Attributes.STEP_HEIGHT, "leg_step", leg ? 0.4 : 0, AttributeModifier.Operation.ADD_VALUE);
        mod(p, Attributes.JUMP_STRENGTH, "klip_jump", klip ? 0.22 * k : 0, AttributeModifier.Operation.ADD_VALUE);
        mod(p, Attributes.SAFE_FALL_DISTANCE, "klip_fall", klip ? 4 : 0, AttributeModifier.Operation.ADD_VALUE);
        int an = d.lvl(Aug.ANGIOGENESIS);
        mod(p, Attributes.MAX_HEALTH, "angio", an == 0 ? 0 : an == 1 ? 4 : 10, AttributeModifier.Operation.ADD_VALUE);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        if (d.has(Aug.SILENCERS) && !p.isSilent()) p.setSilent(true);
    }

    // ================================================================ pedidos do cliente
    public static void handle(ServerPlayer p, Net.Request r) {
        switch (r.op()) {
            case 0 -> { Aug a = Aug.byId(r.id()); if (a != null) upgrade(p, a); }
            case 1 -> { Aug a = Aug.byId(r.id()); if (a != null) use(p, a); }
            case 2 -> overclock(p);
            case 3 -> equip(p, r.id());
            case 5 -> dashAimed(p);
            default -> {}
        }
    }

    static void upgrade(ServerPlayer p, Aug a) {
        AugData d = AugData.get(p);
        if (!a.canUpgrade(d)) return;
        int c = a.nextCost(d);
        if (d.praxis < c) return;
        d.praxis -= c;
        d.levels.merge(a.id, 1, Integer::sum);
        if (a.mode != Aug.Mode.PASSIVE && d.lvl(a) == 1) { // equipa no primeiro slot livre
            boolean already = false;
            for (String sl : d.slots) if (a.id.equals(sl)) already = true;
            if (!already) for (int i = 0; i < 4; i++) if (d.slots[i].isEmpty()) { d.slots[i] = a.id; break; }
        }
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.5f);
        p.displayClientMessage(Component.translatable("msg.nanoaug.installed",
                Component.translatable(a.levelKey(d.lvl(a)))), true);
        applyPassives(p);
        Net.sync(p);
    }

    static void overclock(ServerPlayer p) {
        AugData d = AugData.get(p);
        if (d.levels.isEmpty() || d.overclock > 0 || d.overheat > 0) return;
        if (d.energy < 50f) {
            msg(p, "low_energy");
            return;
        }
        d.overclock = AugData.OVERCLOCK_TICKS;
        d.energy = d.maxEnergy();
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 0.6f);
        Fx.ring(p.serverLevel(), p.position(), 1.5);
        msg(p, "overclock");
        applyPassives(p);
        Net.sync(p);
    }

    static void equip(ServerPlayer p, String spec) {
        AugData d = AugData.get(p);
        String[] sp = spec.split(":", 2);
        int i;
        try { i = Integer.parseInt(sp[0]); } catch (NumberFormatException ex) { return; }
        if (i < 0 || i >= 4) return;
        String id = sp.length > 1 ? sp[1] : "";
        if (id.isEmpty()) { d.slots[i] = ""; Net.sync(p); return; }
        Aug a = Aug.byId(id);
        if (a == null || a.mode == Aug.Mode.PASSIVE || !d.has(a)) return;
        for (int j = 0; j < 4; j++) if (a.id.equals(d.slots[j])) d.slots[j] = "";
        d.slots[i] = a.id;
        Net.sync(p);
    }

    /** Icarus Dash segurado e solto: vai até o ponto mirado (ver DashAim). */
    static void dashAimed(ServerPlayer p) {
        AugData d = AugData.get(p);
        Aug a = Aug.ICARUS_DASH;
        if (!d.has(a)) return;
        if (d.overheat > 0) { msg(p, "offline"); return; }
        if (d.cooldowns.getOrDefault(a.id, 0) > 0) return;
        boolean oc = d.overclock > 0;
        float cost = cost(a, d);
        if (!oc && d.energy < cost) { msg(p, "low_energy"); return; }
        double k = d.potency();
        Vec3 t = DashAim.compute(p, DashAim.range(d.lvl(a)) * k);
        if (t == null) { msg(p, "no_spot"); return; }
        ServerLevel lv = p.serverLevel();
        Vec3 start = p.position();
        if (d.lvl(a) >= 3) { // Charged Dash: derruba inimigos no caminho
            for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class,
                    new AABB(start, t).inflate(1.6), o -> o != p && o instanceof Enemy)) {
                e.hurt(p.damageSources().playerAttack(p), (float) (6 * k));
                Vec3 dir = t.subtract(start).normalize();
                e.setDeltaMovement(dir.x * 1.2, 0.4, dir.z * 1.2);
                e.hurtMarked = true;
            }
        }
        Fx.line(lv, start.add(0, 0.9, 0), t.add(0, 0.9, 0));
        p.connection.teleport(t.x, t.y, t.z, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        Fx.burst(lv, t.add(0, 1, 0), 18);
        lv.playSound(null, p.blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1f, 1.8f);
        if (!oc) {
            d.energy -= cost;
            if (d.energy <= 0) { d.energy = 0; d.regenPause = rechargeDelay(d); }
        }
        d.cooldowns.put(a.id, oc ? cooldown(a) / 2 : cooldown(a));
        Net.sync(p);
    }

    // ---- Punch Through Wall (passivo): mão vazia + segurar bater
    private static boolean punchBlockOk(BlockState st, int l) {
        if (!(st.is(BlockTags.MINEABLE_WITH_PICKAXE) || st.is(BlockTags.PLANKS))) return false;
        float h = st.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return h >= 0 && h <= (l >= 2 ? 3.5f : 2.0f);
    }

    private static boolean punchOk(Player p, BlockState st, AugData d) {
        int l = d.lvl(Aug.PUNCH);
        if (l == 0 || d.overheat > 0 || !p.getMainHandItem().isEmpty()) return false;
        return punchBlockOk(st, l) && (d.overclock > 0 || d.energy >= 8f);
    }

    @SubscribeEvent
    public static void punchSpeed(PlayerEvent.BreakSpeed e) {
        if (punchOk(e.getEntity(), e.getState(), AugData.get(e.getEntity())))
            e.setNewSpeed(Math.max(e.getNewSpeed(), 1000f));
    }

    @SubscribeEvent
    public static void punchHarvest(PlayerEvent.HarvestCheck e) {
        if (punchOk(e.getEntity(), e.getTargetBlock(), AugData.get(e.getEntity()))) e.setCanHarvest(true);
    }

    @SubscribeEvent
    public static void punchArea(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p)) return;
        AugData d = AugData.get(p);
        if (!punchOk(p, e.getState(), d)) return;
        ServerLevel lv = p.serverLevel();
        BlockPos c = e.getPos();
        int l = d.lvl(Aug.PUNCH);
        int r = l >= 2 ? 2 : 1;
        HitResult hr = p.pick(6, 1f, false);
        Direction.Axis axis = hr instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK
                ? bh.getDirection().getAxis() : p.getDirection().getAxis();
        if (d.overclock == 0) {
            d.energy -= 8f;
            if (d.energy <= 0) { d.energy = 0; d.regenPause = rechargeDelay(d); }
        }
        int broken = 0;
        for (int a = -r; a <= r; a++) {
            for (int b = -r; b <= r; b++) {
                if (a == 0 && b == 0) continue;
                BlockPos q = switch (axis) {
                    case X -> c.offset(0, a, b);
                    case Y -> c.offset(a, 0, b);
                    case Z -> c.offset(a, b, 0);
                };
                BlockState qs = lv.getBlockState(q);
                if (!qs.isAir() && punchBlockOk(qs, l) && lv.mayInteract(p, q)) {
                    lv.destroyBlock(q, true, p);
                    broken++;
                }
            }
        }
        if (broken > 0) {
            Fx.burst(lv, Vec3.atCenterOf(c), 25);
            lv.playSound(null, c, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 0.6f);
        }
        Net.sync(p);
    }

    static void msg(ServerPlayer p, String key) { p.displayClientMessage(Component.translatable("msg.nanoaug." + key), true); }

    static void deactivateAll(ServerPlayer p, AugData d) {
        d.active.clear();
        p.removeEffect(MobEffects.INVISIBILITY);
    }

    static void use(ServerPlayer p, Aug a) {
        AugData d = AugData.get(p);
        if (!d.has(a) || a.mode == Aug.Mode.PASSIVE) return;
        if (d.overheat > 0) { msg(p, "offline"); return; }
        ServerLevel lv = p.serverLevel();

        if (a.mode == Aug.Mode.TOGGLE) {
            if (d.active.remove(a.id)) {
                if (a == Aug.CLOAK) p.removeEffect(MobEffects.INVISIBILITY);
                lv.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.4f);
            } else {
                if (d.energy < 5f && d.overclock == 0) { msg(p, "low_energy"); return; }
                d.active.add(a.id);
                lv.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
                Fx.burst(lv, p.position().add(0, 1, 0), 12);
            }
            Net.sync(p);
            return;
        }

        // ---- ACTION
        if (d.cooldowns.getOrDefault(a.id, 0) > 0) return;
        boolean oc = d.overclock > 0;
        float cost = cost(a, d);
        if (!oc && d.energy < cost) { msg(p, "low_energy"); return; }
        double k = d.potency();

        boolean ok = switch (a) {
            case PEPS -> peps(p, lv, d, k);
            case REMOTE_HACK -> hack(p, lv, d);
            case TESLA -> tesla(p, lv, d, k);
            case NANOBLADE -> blade(p, lv, d, k);
            case TYPHOON -> typhoon(p, lv, d, k);
            case ICARUS_DASH -> dash(p, lv, d, k);
            default -> false;
        };
        if (!ok) return;

        if (!oc) {
            d.energy -= cost;
            if (d.energy <= 0) { d.energy = 0; d.regenPause = rechargeDelay(d); }
        }
        d.cooldowns.put(a.id, oc ? cooldown(a) / 2 : cooldown(a));
        Net.sync(p);
    }

    // ================================================================ ações
    private static Entity rayEntity(ServerPlayer p, ServerLevel lv, double range, Predicate<Entity> filter) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        Entity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : lv.getEntities(p, new AABB(eye, end).inflate(1), filter)) {
            Optional<Vec3> c = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (c.isPresent()) {
                double dd = c.get().distanceToSqr(eye);
                if (dd < bd) { bd = dd; best = e; }
            }
        }
        return best;
    }

    private static boolean peps(ServerPlayer p, ServerLevel lv, AugData d, double k) {
        Vec3 look = p.getLookAngle();
        if (p.isShiftKeyDown() && d.lvl(Aug.PEPS) >= 2) { // Precision Diode
            Entity t = rayEntity(p, lv, 14, e -> e instanceof LivingEntity && e instanceof Enemy);
            if (!(t instanceof LivingEntity le)) { msg(p, "no_target"); return false; }
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 9));
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 9));
            le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0));
            if (le instanceof Mob m) m.setTarget(null);
            Fx.arc(lv, p.getEyePosition().add(0, -0.3, 0), le.position().add(0, le.getBbHeight() / 2, 0));
            lv.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6f, 2f);
            return true;
        }
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(7),
                o -> o != p && o instanceof Enemy)) {
            Vec3 to = e.position().subtract(p.position());
            if (to.lengthSqr() < 1e-4 || to.normalize().dot(flat) < 0.5) continue;
            Vec3 dir = to.normalize();
            e.hurt(p.damageSources().playerAttack(p), 2f);
            e.setDeltaMovement(dir.x * 1.8 * k, 0.5, dir.z * 1.8 * k);
            e.hurtMarked = true;
        }
        Vec3 front = p.getEyePosition().add(look.scale(2));
        Fx.burst(lv, front, 30);
        Fx.ring(lv, p.position().add(flat.scale(2)), 1.5);
        lv.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.8f);
        return true;
    }

    /** Hacking remoto: portas/alavancas/botões; nível 2 aumenta alcance; nível 3 desativa inimigo mirado. */
    private static boolean hack(ServerPlayer p, ServerLevel lv, AugData d) {
        int l = d.lvl(Aug.REMOTE_HACK);
        double range = l >= 2 ? 20 : 10;
        Vec3 eye = p.getEyePosition();
        HitResult hr = p.pick(range, 1f, false);
        double bd = hr.getType() == HitResult.Type.BLOCK ? hr.getLocation().distanceTo(eye) : 1e9;

        if (l >= 3) {
            Entity t = rayEntity(p, lv, range, e -> e instanceof Mob && e instanceof Enemy);
            if (t instanceof Mob m && p.distanceTo(m) < bd) {
                m.setTarget(null);
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 5));
                m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 3));
                Fx.arc(lv, eye.add(0, -0.3, 0), m.position().add(0, m.getBbHeight() / 2, 0));
                lv.playSound(null, m.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 2f);
                return true;
            }
        }
        if (hr.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hr;
            BlockPos pos = bhr.getBlockPos();
            BlockState st = lv.getBlockState(pos);
            Block b = st.getBlock();
            boolean ok = false;
            if (b instanceof DoorBlock door) {
                door.setOpen(p, lv, st, pos, !st.getValue(DoorBlock.OPEN));
                ok = true;
            } else if (b instanceof TrapDoorBlock) {
                lv.setBlock(pos, st.cycle(TrapDoorBlock.OPEN), 2);
                lv.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1f, 1f);
                ok = true;
            } else if (b instanceof LeverBlock || b instanceof ButtonBlock || b instanceof FenceGateBlock) {
                st.useWithoutItem(lv, p, bhr);
                ok = true;
            } else if (l >= 2 && b instanceof RedstoneLampBlock) {
                lv.setBlock(pos, st.cycle(RedstoneLampBlock.LIT), 3);
                ok = true;
            }
            if (ok) {
                Fx.arc(lv, eye.add(0, -0.3, 0), Vec3.atCenterOf(pos));
                return true;
            }
        }
        msg(p, "no_target");
        return false;
    }

    private static boolean tesla(ServerPlayer p, ServerLevel lv, AugData d, double k) {
        int n = new int[]{1, 2, 4}[d.lvl(Aug.TESLA) - 1];
        double range = 8 * (1 + d.lvl(Aug.TESLA_RANGE));
        List<LivingEntity> list = lv.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
                o -> o != p && o.isAlive() && o instanceof Enemy && o.distanceTo(p) <= range && p.hasLineOfSight(o));
        list.sort(Comparator.comparingDouble(o -> o.distanceToSqr(p)));
        if (list.isEmpty()) { msg(p, "no_target"); return false; }
        Vec3 from = p.getEyePosition().add(0, -0.4, 0);
        for (int i = 0; i < Math.min(n, list.size()); i++) {
            LivingEntity e = list.get(i);
            e.hurt(p.damageSources().playerAttack(p), (float) (4 * k));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (80 * k), 6));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) (80 * k), 2));
            Fx.arc(lv, from, e.position().add(0, e.getBbHeight() / 2, 0));
        }
        lv.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8f, 1.8f);
        return true;
    }

    private static boolean blade(ServerPlayer p, ServerLevel lv, AugData d, double k) {
        int l = d.lvl(Aug.NANOBLADE);
        Arrow a = new Arrow(EntityType.ARROW, lv);
        a.setOwner(p);
        a.setPos(p.getX(), p.getEyeY() - 0.1, p.getZ());
        a.shootFromRotation(p, p.getXRot(), p.getYRot(), 0f, l >= 2 ? 4.5f : 3.5f, 0f);
        a.setBaseDamage((l >= 2 ? 9 : 6) * k);
        a.setCritArrow(true);
        a.pickup = AbstractArrow.Pickup.DISALLOWED;
        if (l >= 3) a.addTag("nanoaug_explosive");
        lv.addFreshEntity(a);
        lv.playSound(null, p.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1f, 1.6f);
        return true;
    }

    private static boolean typhoon(ServerPlayer p, ServerLevel lv, AugData d, double k) {
        if (d.has(Aug.TYPHOON_LETHAL)) {
            for (int i = 0; i < 16; i++) {
                Arrow a = new Arrow(EntityType.ARROW, lv);
                a.setOwner(p);
                a.setPos(p.getX(), p.getEyeY() - 0.3, p.getZ());
                a.shootFromRotation(p, 0f, i * 22.5f, 0f, 2.2f, 0f);
                a.setBaseDamage(5 * k);
                a.pickup = AbstractArrow.Pickup.DISALLOWED;
                lv.addFreshEntity(a);
            }
        } else if (d.has(Aug.TYPHOON_STUN)) {
            for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8),
                    o -> o != p && o instanceof Enemy)) {
                e.hurt(p.damageSources().playerAttack(p), 1f);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (100 * k), 6));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) (100 * k), 3));
            }
        } else {
            msg(p, "choose_config");
            return false;
        }
        Fx.ring(lv, p.position(), 3);
        Fx.ring(lv, p.position().add(0, 0.6, 0), 5);
        lv.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1f, 1.2f);
        return true;
    }

    private static boolean dash(ServerPlayer p, ServerLevel lv, AugData d, double k) {
        int l = d.lvl(Aug.ICARUS_DASH);
        Vec3 look = p.getLookAngle();
        Vec3 h = new Vec3(look.x, 0, look.z);
        h = h.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : h.normalize();
        double power = (l >= 2 ? 1.9 : 1.6) * k;
        Vec3 start = p.position();
        if (l >= 3) { // Charged Dash: derruba inimigos no caminho
            for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class,
                    p.getBoundingBox().expandTowards(h.scale(5)).inflate(1), o -> o != p && o instanceof Enemy)) {
                e.hurt(p.damageSources().playerAttack(p), (float) (6 * k));
                e.setDeltaMovement(h.x * 1.2, 0.4, h.z * 1.2);
                e.hurtMarked = true;
            }
        }
        p.setDeltaMovement(h.x * power, 0.25 + Math.max(0, look.y) * 0.5, h.z * power);
        p.hurtMarked = true;
        p.fallDistance = 0;
        Fx.trail(lv, start, h);
        lv.playSound(null, p.blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1f, 1.4f);
        return true;
    }

    // ================================================================ tick
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        AugData d = AugData.get(p);
        if (d.levels.isEmpty()) return;
        ServerLevel lv = p.serverLevel();
        float max = d.maxEnergy();

        d.cooldowns.replaceAll((key, v) -> Math.max(0, v - 1));

        if (d.overheat > 0) {
            if (--d.overheat == 0) msg(p, "online");
        } else if (d.overclock > 0) {
            d.energy = max;
            if (--d.overclock == 0) { // fim do overclock -> sobreaquecimento
                d.overheat = AugData.OVERHEAT_TICKS;
                d.energy = 0;
                deactivateAll(p, d);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                lv.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1f, 0.5f);
                msg(p, "overheat");
                applyPassives(p);
            }
        } else {
            if (d.regenPause > 0) d.regenPause--;
            else if (d.active.isEmpty())
                d.energy = Math.min(max, d.energy + 0.25f * (1f + 0.5f * d.lvl(Aug.SARIF_RATE)));
        }

        // gasto contínuo dos augs ligados
        if (d.overclock == 0 && !d.active.isEmpty()) {
            float dr = 0;
            for (String id : d.active) {
                Aug a = Aug.byId(id);
                if (a != null) dr += drain(a, d, p);
            }
            d.energy -= dr;
            if (d.energy <= 0) {
                d.energy = 0;
                d.regenPause = rechargeDelay(d);
                deactivateAll(p, d);
                msg(p, "low_energy");
            }
        }

        if (d.active(Aug.CLOAK))
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, false, false, false));
        if (d.active(Aug.TITAN))
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 5, 4, false, false, false));

        // Rebreather
        if (d.has(Aug.REBREATHER) && d.overheat == 0 && p.isUnderWater() && (d.overclock > 0 || d.energy > 0.5f)) {
            if (p.getAirSupply() < p.getMaxAirSupply()) p.setAirSupply(p.getMaxAirSupply());
            if (d.overclock == 0) {
                d.energy = Math.max(0, d.energy - 0.03f);
                d.regenPause = Math.max(d.regenPause, 20);
            }
        }

        // Sentinel RX: regeneração
        int sl = d.lvl(Aug.SENTINEL);
        if (sl > 0 && p.tickCount % 20 == 0 && p.getHealth() < p.getMaxHealth()
                && p.tickCount - d.lastHurt >= regenDelay(d))
            p.heal((float) (new float[]{0.5f, 1f, 2f}[sl - 1] * d.potency()));

        if (p.tickCount % 10 == 0) {
            applyPassives(p);
            if (d.lvl(Aug.REBREATHER) >= 2) p.removeEffect(MobEffects.POISON);

            if (d.active(Aug.SMART_VISION)) {
                int l = d.lvl(Aug.SMART_VISION);
                double r = l >= 2 ? 24 : 16;
                for (Mob m : lv.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r), o -> o instanceof Enemy))
                    if (l >= 2 || p.hasLineOfSight(m))
                        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false, false));
            }
            if (d.has(Aug.MAGPIE)) {
                boolean on = d.active(Aug.SMART_VISION);
                for (ItemEntity it : lv.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(40)))
                    it.setGlowingTag(on && it.distanceTo(p) <= 16);
            }
        }
        if (p.tickCount % 5 == 0) Net.sync(p);
    }

    // ================================================================ eventos de combate
    @SubscribeEvent
    public static void fall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        AugData d = AugData.get(p);
        int l = d.lvl(Aug.ICARUS_LANDING);
        if (l == 0) return;
        float dist = e.getDistance();
        e.setDamageMultiplier(0f);
        if (l >= 2 && dist >= 5) { // Icarus Strike
            ServerLevel lv = p.serverLevel();
            for (LivingEntity t : lv.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4),
                    o -> o != p && o instanceof Enemy)) {
                t.hurt(p.damageSources().playerAttack(p), (float) (dist * 0.8 * d.potency()));
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 5));
            }
            Fx.ring(lv, p.position(), 2);
            Fx.ring(lv, p.position(), 4);
            Fx.burst(lv, p.position().add(0, 0.3, 0), 30);
            lv.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.9f, 1.2f);
        }
    }

    @SubscribeEvent
    public static void incomingDamage(LivingIncomingDamageEvent e) {
        if (e.getEntity() instanceof Player victim) {
            AugData d = AugData.get(victim);
            d.lastHurt = victim.tickCount;
            int r = d.lvl(Aug.RHINO);
            if (r > 0 && !e.getSource().is(DamageTypeTags.BYPASSES_ARMOR))
                e.setAmount(e.getAmount() * (1f - new float[]{0.20f, 0.33f, 0.50f}[r - 1]));
            if (d.has(Aug.EMP_SHIELD) && e.getSource().is(DamageTypeTags.IS_LIGHTNING)) e.setAmount(0f);
        }
        if (e.getSource().getEntity() instanceof ServerPlayer atk && e.getSource().getDirectEntity() == atk) {
            AugData d = AugData.get(atk);
            // Instant Takedown (passivo): mão vazia + inimigo desprevenido (ou você agachado/camuflado)
            if (!chaining && d.has(Aug.TAKEDOWN) && d.overheat == 0 && atk.getMainHandItem().isEmpty()
                    && e.getEntity() instanceof LivingEntity v && v instanceof Enemy) {
                boolean stealth = atk.isShiftKeyDown() || d.active(Aug.CLOAK);
                boolean unaware = !(v instanceof Mob m) || m.getTarget() != atk;
                float tc = d.lvl(Aug.TAKEDOWN) >= 2 ? 10f : 20f;
                if ((stealth || unaware) && (d.overclock > 0 || d.energy >= tc)) {
                    if (d.overclock == 0) {
                        d.energy -= tc;
                        if (d.energy <= 0) { d.energy = 0; d.regenPause = rechargeDelay(d); }
                    }
                    e.setAmount(Math.max(e.getAmount(), 1000f));
                    ServerLevel lv = atk.serverLevel();
                    Fx.burst(lv, v.position().add(0, 1, 0), 18);
                    if (d.lvl(Aug.QUICKSILVER) >= 2) { // Multiple Takedown
                        lv.getEntitiesOfClass(LivingEntity.class, v.getBoundingBox().inflate(3),
                                        o -> o != v && o != atk && o instanceof Enemy)
                                .stream().min(Comparator.comparingDouble(o -> o.distanceToSqr(v)))
                                .ifPresent(o -> {
                                    chaining = true;
                                    o.hurt(atk.damageSources().playerAttack(atk), 1000f);
                                    chaining = false;
                                });
                    }
                    Net.sync(atk);
                } else {
                    e.setAmount(e.getAmount() + 4f * (float) d.potency()); // soco forte
                }
            }
            if (d.active(Aug.CLOAK) && !d.has(Aug.CLOAK_TAKEDOWN)) { // atacar desfaz a camuflagem
                d.active.remove(Aug.CLOAK.id);
                atk.removeEffect(MobEffects.INVISIBILITY);
                Net.sync(atk);
            }
        }
    }

    @SubscribeEvent
    public static void retarget(LivingChangeTargetEvent e) {
        if (e.getNewAboutToBeSetTarget() instanceof ServerPlayer p && AugData.get(p).active(Aug.CLOAK))
            e.setNewAboutToBeSetTarget(null);
    }

    @SubscribeEvent
    public static void impact(ProjectileImpactEvent e) {
        Entity pr = e.getProjectile();
        if (pr.level().isClientSide || !pr.getTags().contains("nanoaug_explosive")) return;
        pr.removeTag("nanoaug_explosive");
        pr.level().explode(pr, pr.getX(), pr.getY(), pr.getZ(), 2.4f, Level.ExplosionInteraction.NONE);
    }

    /** Reload Speed Dexterity: arcos, bestas e tridentes carregam mais rápido. */
    @SubscribeEvent
    public static void useTick(LivingEntityUseItemEvent.Tick e) {
        if (!(e.getEntity() instanceof Player p)) return;
        int l = AugData.get(p).lvl(Aug.RELOAD);
        if (l == 0) return;
        Item it = e.getItem().getItem();
        if ((it instanceof BowItem || it instanceof CrossbowItem || it instanceof TridentItem)
                && (l >= 2 || p.tickCount % 2 == 0))
            e.setDuration(e.getDuration() - 1);
    }

    // ================================================================ CASIE: desconto com aldeões
    @SubscribeEvent
    public static void tradeOpen(PlayerContainerEvent.Open e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getContainer() instanceof MerchantMenu mm)) return;
        AugData d = AugData.get(p);
        int l = d.lvl(Aug.SOCIAL);
        if (l == 0) return;
        double pct = (l >= 2 ? 0.30 : 0.15) * d.potency();
        for (MerchantOffer o : mm.getOffers()) {
            int n = (int) Math.ceil(o.getBaseCostA().getCount() * pct);
            n = Math.min(n, o.getCostA().getCount() - 1);
            if (n > 0) o.addToSpecialPriceDiff(-n); // a vanilla zera isso quando a troca termina
        }
    }

    // ================================================================ Kits Praxis pelo mundo
    private static final Map<String, Float> LOOT = Map.ofEntries(
            Map.entry("ancient_city", 0.35f), Map.entry("stronghold", 0.30f), Map.entry("bastion", 0.35f),
            Map.entry("end_city", 0.40f), Map.entry("trial_chambers", 0.30f), Map.entry("woodland_mansion", 0.35f),
            Map.entry("pillager_outpost", 0.25f), Map.entry("desert_pyramid", 0.10f), Map.entry("jungle_temple", 0.10f),
            Map.entry("village", 0.08f), Map.entry("simple_dungeon", 0.05f), Map.entry("abandoned_mineshaft", 0.04f));

    @SubscribeEvent
    public static void loot(LootTableLoadEvent e) {
        ResourceLocation id = e.getName();
        if (!id.getNamespace().equals("minecraft") || !id.getPath().startsWith("chests/")) return;
        for (Map.Entry<String, Float> en : LOOT.entrySet()) {
            if (id.getPath().contains(en.getKey())) {
                e.getTable().addPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1))
                        .when(LootItemRandomChanceCondition.randomChance(en.getValue()))
                        .add(LootItem.lootTableItem(NanoAug.PRAXIS_KIT.get()))
                        .build());
                break;
            }
        }
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent e) {
        if (!(e.getSource().getEntity() instanceof Player)) return;
        LivingEntity v = e.getEntity();
        EntityType<?> t = v.getType();
        RandomSource r = v.getRandom();
        int n = 0;
        if (t == EntityType.ENDER_DRAGON) n = 3;
        else if (t == EntityType.WITHER || t == EntityType.WARDEN) n = 2;
        else if (t == EntityType.ELDER_GUARDIAN && r.nextFloat() < 0.5f) n = 1;
        else if (t == EntityType.RAVAGER && r.nextFloat() < 0.25f) n = 1;
        else if (t == EntityType.EVOKER && r.nextFloat() < 0.15f) n = 1;
        if (n > 0)
            e.getDrops().add(new ItemEntity(v.level(), v.getX(), v.getY(), v.getZ(),
                    new ItemStack(NanoAug.PRAXIS_KIT.get(), n)));
    }

    // ================================================================ sincronização
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { applyPassives(p); Net.sync(p); }
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { applyPassives(p); Net.sync(p); }
    }
    @SubscribeEvent public static void dim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Net.sync(p);
    }
}
