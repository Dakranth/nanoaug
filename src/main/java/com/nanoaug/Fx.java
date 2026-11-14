package com.nanoaug;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Efeitos amarelos (raios, ondas, rastros) estilo Deus Ex. */
public final class Fx {
    public static final DustParticleOptions YELLOW = new DustParticleOptions(new Vector3f(1f, 0.78f, 0.1f), 1.3f);

    private Fx() {}

    /** Raio em zigue-zague entre dois pontos. */
    public static void arc(ServerLevel lv, Vec3 a, Vec3 b) {
        RandomSource r = lv.getRandom();
        int n = 16;
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double j = (i == 0 || i == n) ? 0 : 0.28;
            lv.sendParticles(YELLOW,
                    a.x + (b.x - a.x) * t + (r.nextDouble() - 0.5) * j * 2,
                    a.y + (b.y - a.y) * t + (r.nextDouble() - 0.5) * j * 2,
                    a.z + (b.z - a.z) * t + (r.nextDouble() - 0.5) * j * 2,
                    1, 0, 0, 0, 0);
        }
    }

    public static void burst(ServerLevel lv, Vec3 c, int count) {
        lv.sendParticles(YELLOW, c.x, c.y, c.z, count, 0.4, 0.4, 0.4, 0.05);
    }

    public static void ring(ServerLevel lv, Vec3 c, double radius) {
        int n = 32;
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n;
            lv.sendParticles(YELLOW, c.x + Math.cos(ang) * radius, c.y + 0.3, c.z + Math.sin(ang) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Rastro atrás de quem avança na direção dir. */
    public static void trail(ServerLevel lv, Vec3 start, Vec3 dir) {
        for (int i = 0; i < 12; i++) {
            Vec3 p = start.subtract(dir.scale(i * 0.4));
            lv.sendParticles(YELLOW, p.x, p.y + 0.9, p.z, 2, 0.15, 0.4, 0.15, 0);
        }
    }
}
