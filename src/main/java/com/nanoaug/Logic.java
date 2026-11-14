package com.nanoaug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

@EventBusSubscriber(modid = NanoAug.MODID)
public class Logic {

    // ---------------------------------------------------------------- passivos (atributos)
    private record PassiveMod(Aug aug, Holder<Attribute> attr, double amount, AttributeModifier.Operation op) {}

    private static final List<PassiveMod> MODS = List.of(
            new PassiveMod(Aug.DERMAL, Attributes.ARMOR, 6, AttributeModifier.Operation.ADD_VALUE),
            new PassiveMod(Aug.DERMAL, Attributes.ARMOR_TOUGHNESS, 2, AttributeModifier.Operation.ADD_VALUE),
            new PassiveMod(Aug.STRENGTH, Attributes.BLOCK_BREAK_SPEED, 0.6, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            new PassiveMod(Aug.STRENGTH, Attributes.ATTACK_DAMAGE, 2, AttributeModifier.Operation.ADD_VALUE),
            new PassiveMod(Aug.REFLEX, Attributes.ATTACK_SPEED, 1.0, AttributeModifier.Operation.ADD_VALUE),
            new PassiveMod(Aug.JUMP, Attributes.JUMP_STRENGTH, 0.18, AttributeModifier.Operation.ADD_VALUE),
            new PassiveMod(Aug.JUMP, Attributes.SAFE_FALL_DISTANCE, 3, AttributeModifier.Operation.ADD_VALUE),
            new PassiveMod(Aug.SERVO, Attributes.MOVEMENT_SPEED, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
    );

    static void applyPassives(ServerPlayer p) {
        AugData d = AugData.get(p);
        double k = d.potency();
        int i = 0;
        for (PassiveMod m : MODS) {
            AttributeInstance inst = p.getAttribute(m.attr());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NanoAug.MODID, m.aug().id + "_" + (i++));
            if (inst == null) continue;
            if (d.has(m.aug())) inst.addOrUpdateTransientModifier(new AttributeModifier(id, m.amount() * k, m.op()));
            else inst.removeModifier(id);
        }
    }

    // ---------------------------------------------------------------- pedidos do cliente
    public static void handle(ServerPlayer p, Net.Request r) {
        switch (r.op()) {
            case 0 -> { Aug a = Aug.byId(r.id()); if (a != null) install(p, a); }
            case 1 -> { Aug a = Aug.byId(r.id()); if (a != null) use(p, a); }
            case 2 -> overclock(p);
            default -> {}
        }
    }

    static void install(ServerPlayer p, Aug a) {
        AugData d = AugData.get(p);
        if (d.has(a) || d.praxis < a.cost) return;
        d.praxis -= a.cost;
        d.installed.add(a.id);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.5f);
        p.displayClientMessage(Component.translatable("msg.nanoaug.installed", Component.translatable(a.nameKey())), true);
        applyPassives(p);
        Net.sync(p);
    }

    static void overclock(ServerPlayer p) {
        AugData d = AugData.get(p);
        if (d.installed.isEmpty() || d.overclock > 0 || d.overheat > 0) return;
        if (d.energy < 50f) {
            p.displayClientMessage(Component.translatable("msg.nanoaug.low_energy"), true);
            return;
        }
        d.overclock = AugData.OVERCLOCK_TICKS;
        d.energy = AugData.MAX_ENERGY;
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 0.6f);
        p.displayClientMessage(Component.translatable("msg.nanoaug.overclock"), true);
        applyPassives(p);
        Net.sync(p);
    }

    static void deactivateAll(ServerPlayer p, AugData d) {
        d.active.clear();
        p.removeEffect(MobEffects.NIGHT_VISION);
        p.removeEffect(MobEffects.INVISIBILITY);
    }

    static void use(ServerPlayer p, Aug a) {
        AugData d = AugData.get(p);
        if (!d.has(a) || a.mode == Aug.Mode.PASSIVE) return;
        if (d.overheat > 0) {
            p.displayClientMessage(Component.translatable("msg.nanoaug.offline"), true);
            return;
        }
        ServerLevel lv = p.serverLevel();

        if (a.mode == Aug.Mode.TOGGLE) {
            if (d.active.remove(a.id)) {
                if (a == Aug.SMART_VISION) p.removeEffect(MobEffects.NIGHT_VISION);
                if (a == Aug.CLOAK) p.removeEffect(MobEffects.INVISIBILITY);
                lv.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.4f);
            } else {
                if (d.energy < 5f && d.overclock == 0) {
                    p.displayClientMessage(Component.translatable("msg.nanoaug.low_energy"), true);
                    return;
                }
                d.active.add(a.id);
                lv.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
            }
            Net.sync(p);
            return;
        }

        // ACTION
        if (d.cooldowns.getOrDefault(a.id, 0) > 0) return;
        boolean oc = d.overclock > 0;
        if (!oc && d.energy < a.energy) {
            p.displayClientMessage(Component.translatable("msg.nanoaug.low_energy"), true);
            return;
        }
        if (!oc) d.energy -= a.energy;
        d.cooldowns.put(a.id, oc ? a.cooldown / 2 : a.cooldown);
        double k = d.potency();

        switch (a) {
            case ICARUS_DASH -> dash(p, lv, k);
            case TITAN -> {
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, (int) (100 * k), 4, false, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, (int) (100 * k), 1, false, false, true));
                lv.playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.5f);
            }
            case TYPHOON -> typhoon(p, lv, k);
            case TESLA -> tesla(p, lv, k);
            case REMOTE_HACK -> {
                if (!hack(p, lv, k)) { // nada para hackear: devolve a energia
                    if (!oc) d.energy += a.energy;
                    d.cooldowns.put(a.id, 0);
                    p.displayClientMessage(Component.translatable("msg.nanoaug.no_target"), true);
                }
            }
            default -> {}
        }
        Net.sync(p);
    }

    // ---------------------------------------------------------------- ações
    private static void dash(ServerPlayer p, ServerLevel lv, double k) {
        Vec3 l = p.getLookAngle();
        Vec3 h = new Vec3(l.x, 0, l.z);
        h = h.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : h.normalize();
        h = h.scale(1.6 * k);
        p.setDeltaMovement(h.x, 0.25 + Math.max(0, l.y) * 0.5, h.z);
        p.hurtMarked = true;
        p.fallDistance = 0;
        lv.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 14, 0.3, 0.1, 0.3, 0.05);
        lv.playSound(null, p.blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1f, 1.4f);
    }

    private static void typhoon(ServerPlayer p, ServerLevel lv, double k) {
        List<LivingEntity> hit = lv.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6),
                e -> e != p && e.isAlive() && e instanceof Enemy);
        for (LivingEntity e : hit) {
            e.hurt(p.damageSources().playerAttack(p), (float) (14 * k));
            e.knockback(1.2, p.getX() - e.getX(), p.getZ() - e.getZ());
        }
        lv.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
        lv.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1f, 1.2f);
    }

    private static void tesla(ServerPlayer p, ServerLevel lv, double k) {
        List<LivingEntity> hit = lv.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(5),
                e -> e != p && e.isAlive() && e instanceof Enemy);
        for (LivingEntity e : hit) {
            e.hurt(p.damageSources().playerAttack(p), (float) (4 * k));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (60 * k), 6));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) (60 * k), 2));
            lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 1, e.getZ(), 20, 0.3, 0.5, 0.3, 0.2);
        }
        lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 30, 0.6, 0.6, 0.6, 0.3);
        lv.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8f, 1.8f);
    }

    /** Hacking remoto: portas/alavancas/botões à distância ou atordoa um inimigo mirado. */
    private static boolean hack(ServerPlayer p, ServerLevel lv, double k) {
        double range = 12;
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        HitResult hr = p.pick(range, 1f, false);
        double blockDist = hr.getType() == HitResult.Type.BLOCK ? hr.getLocation().distanceTo(eye) : 1e9;

        Mob best = null;
        double bestDist = 1e9;
        for (Entity e : lv.getEntities(p, new AABB(eye, end).inflate(1), x -> x instanceof Mob && x instanceof Enemy)) {
            Optional<Vec3> c = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (c.isPresent()) {
                double dd = c.get().distanceTo(eye);
                if (dd < bestDist) { bestDist = dd; best = (Mob) e; }
            }
        }
        if (best != null && bestDist < blockDist) {
            best.setTarget(null);
            best.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (100 * k), 5));
            best.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) (100 * k), 3));
            lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, best.getX(), best.getY() + 1, best.getZ(), 25, 0.3, 0.5, 0.3, 0.2);
            lv.playSound(null, best.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 2f);
            return true;
        }
        if (hr instanceof BlockHitResult bhr && hr.getType() == HitResult.Type.BLOCK) {
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
            }
            if (ok) {
                lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 15, 0.3, 0.3, 0.3, 0.1);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- tick
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        AugData d = AugData.get(p);
        if (d.installed.isEmpty()) return;

        d.cooldowns.replaceAll((key, v) -> Math.max(0, v - 1));

        if (d.overheat > 0) {
            d.overheat--;
            if (d.overheat == 0) p.displayClientMessage(Component.translatable("msg.nanoaug.online"), true);
        } else if (d.overclock > 0) {
            if (--d.overclock == 0) { // fim do overclock -> sobreaquecimento
                d.overheat = AugData.OVERHEAT_TICKS;
                d.energy = 0;
                deactivateAll(p, d);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1f, 0.5f);
                p.displayClientMessage(Component.translatable("msg.nanoaug.overheat"), true);
                applyPassives(p);
            }
        } else {
            d.energy = Math.min(AugData.MAX_ENERGY, d.energy + 0.25f);
        }

        // gasto contínuo dos aprimoramentos ligados
        if (d.overclock == 0 && !d.active.isEmpty()) {
            float drain = 0;
            for (String id : d.active) {
                Aug a = Aug.byId(id);
                if (a != null) drain += a.energy;
            }
            d.energy -= drain;
            if (d.energy <= 0) {
                d.energy = 0;
                deactivateAll(p, d);
                p.displayClientMessage(Component.translatable("msg.nanoaug.low_energy"), true);
            }
        }

        if (d.active(Aug.CLOAK))
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, false, false, false));

        if (p.tickCount % 10 == 0) {
            applyPassives(p);
            if (d.active(Aug.SMART_VISION)) {
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false, false));
                for (Monster m : p.level().getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(24)))
                    m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false, false));
            }
        }
        if (d.has(Aug.NANO_REPAIR) && p.tickCount % 40 == 0 && p.getHealth() < p.getMaxHealth())
            p.heal((float) d.potency());

        if (p.tickCount % 5 == 0) Net.sync(p);
    }

    // ---------------------------------------------------------------- eventos de combate
    @SubscribeEvent
    public static void fall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        AugData d = AugData.get(p);
        if (!d.has(Aug.ICARUS_LANDING)) return;
        float dist = e.getDistance();
        if (dist < 3) return;
        if (d.overclock == 0) {
            if (d.energy < 10f) return; // sem energia, leva dano normal
            d.energy -= 10f;
        }
        e.setDamageMultiplier(0f);
        if (dist >= 5) {
            ServerLevel lv = p.serverLevel();
            for (LivingEntity t : lv.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4),
                    x -> x != p && x instanceof Enemy))
                t.hurt(p.damageSources().playerAttack(p), (float) (dist * 0.8 * d.potency()));
            lv.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.2, p.getZ(), 6, 1.2, 0.1, 1.2, 0);
            lv.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8f, 1.4f);
        }
        Net.sync(p);
    }

    @SubscribeEvent
    public static void incomingDamage(LivingIncomingDamageEvent e) {
        if (e.getEntity() instanceof Player victim) {
            AugData d = AugData.get(victim);
            if (d.has(Aug.BLAST_SKIN)) {
                if (e.getSource().is(DamageTypeTags.IS_EXPLOSION)) e.setAmount(e.getAmount() * 0.4f);
                else if (e.getSource().is(DamageTypeTags.IS_FIRE)) e.setAmount(e.getAmount() * 0.6f);
            }
        }
        if (e.getSource().getEntity() instanceof Player atk && e.getSource().getDirectEntity() == atk
                && atk.getMainHandItem().isEmpty()) {
            AugData d = AugData.get(atk);
            if (d.has(Aug.NANO_BLADE)) e.setAmount(e.getAmount() + (float) (5 * d.potency()));
        }
    }

    @SubscribeEvent
    public static void retarget(LivingChangeTargetEvent e) {
        if (e.getNewAboutToBeSetTarget() instanceof ServerPlayer p && AugData.get(p).active(Aug.CLOAK))
            e.setNewAboutToBeSetTarget(null);
    }

    // ---------------------------------------------------------------- CASIE: desconto com aldeões
    @SubscribeEvent
    public static void tradeOpen(PlayerContainerEvent.Open e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getContainer() instanceof MerchantMenu mm)) return;
        AugData d = AugData.get(p);
        if (!d.has(Aug.SOCIAL)) return;
        double pct = 0.30 * d.potency();
        for (MerchantOffer o : mm.getOffers()) {
            int n = (int) Math.ceil(o.getBaseCostA().getCount() * pct);
            n = Math.min(n, o.getCostA().getCount() - 1);
            if (n > 0) o.addToSpecialPriceDiff(-n); // a vanilla zera isso quando a troca termina
        }
    }

    // ---------------------------------------------------------------- Kits Praxis pelo mundo
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

    // ---------------------------------------------------------------- sincronização
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
