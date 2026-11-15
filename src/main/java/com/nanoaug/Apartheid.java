package com.nanoaug;

/** "Apartheid Mecânico": aldeões e golens desconfiam de quem exibe próteses. */
public final class Apartheid {
    private Apartheid() {}

    /** quantas próteses visíveis (braço, perna) */
    public static int visible(AugData d) { return (d.has(Aug.ARM) ? 1 : 0) + (d.has(Aug.LEG) ? 1 : 0); }

    /** camuflado ou com Dialectic Enhancer: ninguém desconfia */
    public static boolean covered(AugData d) { return d.active(Aug.CLOAK) || d.lvl(Aug.SOCIAL) >= 2; }

    public static boolean wary(AugData d) { return visible(d) > 0 && !covered(d); }

    /** + = desconto, - = acréscimo (fração do preço) */
    public static double priceMod(AugData d) {
        int l = d.lvl(Aug.SOCIAL);
        double discount = (l >= 2 ? 0.35 : l == 1 ? 0.15 : 0) * d.potency();
        double penalty = d.active(Aug.CLOAK) ? 0 : 0.15 * visible(d);
        return discount - penalty;
    }
}
