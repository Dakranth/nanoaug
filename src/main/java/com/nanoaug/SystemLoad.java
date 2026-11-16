package com.nanoaug;

/** Carga do sistema: 100% + peso de cada aug experimental ativo. O Calibrador reduz o peso. */
public final class SystemLoad {
    private SystemLoad() {}

    /** peso (em %) de cada experimental ativo, por nível de calibração (0 = sem calibrar). */
    public static int weightFor(int tier) { return new int[]{50, 25, 10, 0}[Math.max(0, Math.min(3, tier))]; }

    public static int load(AugData d) {
        int n = 0;
        for (Aug a : Aug.values()) if (a.experimental() && d.has(a)) n++; // has = instalado e fora do standby
        return 100 + n * weightFor(d.calib);
    }

    private static final int[][] PTS = {{100, 0}, {150, 10}, {200, 20}, {250, 32}, {300, 45}, {350, 60}};

    /** chance de falha (%) por uso de um aug experimental. */
    public static float failPct(AugData d) {
        int l = load(d);
        if (l <= 100) return 0f;
        for (int i = 1; i < PTS.length; i++) {
            if (l <= PTS[i][0]) {
                float t = (l - PTS[i - 1][0]) / (float) (PTS[i][0] - PTS[i - 1][0]);
                return PTS[i - 1][1] + t * (PTS[i][1] - PTS[i - 1][1]);
            }
        }
        return 60f;
    }
}
