package com.nanoaug;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class AugData {
    public static final int OVERCLOCK_TICKS = 300; // 15 s
    public static final int OVERHEAT_TICKS = 300;  // 15 s offline depois do overclock

    public static final Codec<AugData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("praxis", 0).forGetter(d -> d.praxis),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("levels", Map.of()).forGetter(d -> d.levels),
            Codec.STRING.listOf().optionalFieldOf("active", List.of()).forGetter(d -> new ArrayList<>(d.active)),
            Codec.FLOAT.optionalFieldOf("energy", 100f).forGetter(d -> d.energy),
            Codec.INT.optionalFieldOf("overclock", 0).forGetter(d -> d.overclock),
            Codec.INT.optionalFieldOf("overheat", 0).forGetter(d -> d.overheat)
    ).apply(i, AugData::new));

    public int praxis;
    public final Map<String, Integer> levels = new LinkedHashMap<>();
    public final Set<String> active = new HashSet<>();
    public float energy = 100f;
    public int overclock;
    public int overheat;

    // só no servidor, não é salvo
    public final Map<String, Integer> cooldowns = new HashMap<>();
    public int lastHurt;
    public int regenPause;

    public AugData() {}

    public AugData(int praxis, Map<String, Integer> lv, List<String> act, float energy, int oc, int oh) {
        this.praxis = praxis;
        this.levels.putAll(lv);
        this.active.addAll(act);
        this.energy = energy;
        this.overclock = oc;
        this.overheat = oh;
    }

    public static AugData get(Player p) { return p.getData(NanoAug.AUG_DATA.get()); }

    public int lvl(Aug a) { return levels.getOrDefault(a.id, 0); }
    public boolean has(Aug a) { return lvl(a) > 0; }
    public boolean active(Aug a) { return active.contains(a.id); }
    public boolean any(Aug.Slot s) {
        for (Aug a : Aug.of(s)) if (has(a)) return true;
        return false;
    }
    public double potency() { return overclock > 0 ? 1.5 : 1.0; }
    public float maxEnergy() { return 100f * (1f + 0.25f * lvl(Aug.SARIF_CAPACITY)); }
}
