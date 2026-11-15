package com.nanoaug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Onde o Icarus Dash mirado te leva. Usado pelo cliente (marcador) e pelo servidor (execução),
 * então as duas pontas sempre concordam. Mirar para cima/para baixo/para o lado muda o destino;
 * mirar na parede logo abaixo de uma borda faz você subir nela.
 */
public final class DashAim {
    private DashAim() {}

    public static double range(int lvl) { return lvl >= 2 ? 14 : 10; }

    /** Posição dos pés no destino, ou null se não houver lugar. */
    public static Vec3 compute(Player p, double range) {
        Level lv = p.level();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult hit = lv.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 t;
        if (hit.getType() == HitResult.Type.MISS) {
            t = settle(lv, p, end.add(0, -0.9, 0), 8);
        } else {
            Direction f = hit.getDirection();
            BlockPos b = hit.getBlockPos();
            Vec3 loc = hit.getLocation();
            if (f == Direction.UP) {
                Vec3 c = loc.add(0, 0.01, 0);
                t = fits(lv, p, c) ? c : null;
            } else if (f == Direction.DOWN) {
                t = settle(lv, p, loc.add(0, -1.9, 0), 8);
            } else {
                t = null;
                for (int k = 1; k <= 3; k++) { // subir em bordas de até 3 blocos
                    BlockPos s = b.above(k);
                    if (loc.y < s.getY() - 2.2) break;
                    if (free(lv, s) && free(lv, s.above()) && !free(lv, s.below())) {
                        Vec3 c = new Vec3(s.getX() + 0.5, s.getY() + 0.01, s.getZ() + 0.5);
                        if (fits(lv, p, c)) { t = c; break; }
                    }
                }
                if (t == null) t = settle(lv, p, loc.add(f.getStepX() * 0.45, -0.9, f.getStepZ() * 0.45), 8);
            }
        }
        if (t == null || t.distanceToSqr(p.position()) < 2.0) return null;
        return t;
    }

    private static boolean free(Level lv, BlockPos pos) {
        return lv.getBlockState(pos).getCollisionShape(lv, pos).isEmpty();
    }

    private static boolean fits(Level lv, Player p, Vec3 feet) {
        return lv.noCollision(p, p.getBoundingBox().move(feet.x - p.getX(), feet.y - p.getY(), feet.z - p.getZ()));
    }

    /** Ajusta o ponto: sai de dentro de blocos e desce até o chão (até maxDrop blocos). */
    private static Vec3 settle(Level lv, Player p, Vec3 start, int maxDrop) {
        Vec3 c = start;
        for (int i = 0; i < 6 && !fits(lv, p, c); i++) c = c.add(0, 0.5, 0);
        if (!fits(lv, p, c)) return null;
        Vec3 top = c;
        for (int i = 0; i < maxDrop * 4; i++) {
            Vec3 n = c.add(0, -0.25, 0);
            if (!fits(lv, p, n)) return c; // achou o chão
            c = n;
        }
        return top; // sem chão por perto: fica no ar, onde você mirou
    }
}
