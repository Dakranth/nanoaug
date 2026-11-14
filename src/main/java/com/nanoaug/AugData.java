package com.nanoaug;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class AugData {
    public static final float MAX_ENERGY = 100f;
    public static final int OVERCLOCK_TICKS = 300; // 15 s
    public static final int OVERHEAT_TICKS = 300;  // 15 s offline depois do overclock

    public static final Codec<AugData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("praxis", 0).forGetter(d -> d.praxis),
            Codec.STRING.listOf().optionalFieldOf("installed", List.of()).forGetter(d -> new ArrayList<>(d.installed)),
            Codec.STRING.listOf().optionalFieldOf("active", List.of()).forGetter(d -> new ArrayList<>(d.active)),
            Codec.FLOAT.optionalFieldOf("energy", MAX_ENERGY).forGetter(d -> d.energy),
            Codec.INT.optionalFieldOf("overclock", 0).forGetter(d -> d.overclock),
            Codec.INT.optionalFieldOf("overheat", 0).forGetter(d -> d.overheat)
    ).apply(i, AugData::new));

    public int praxis;
    public final Set<String> installed = new LinkedHashSet<>();
    public final Set<String> active = new HashSet<>();
    public float energy = MAX_ENERGY;
    public int overclock;
    public int overheat;
    /** só no servidor, não é salvo */
    public final Map<String, Integer> cooldowns = new HashMap<>();

    public AugData() {}

    public AugData(int praxis, List<String> inst, List<String> act, float energy, int oc, int oh) {
        this.praxis = praxis;
        this.installed.addAll(inst);
        this.active.addAll(act);
        this.energy = energy;
        this.overclock = oc;
        this.overheat = oh;
    }

    public static AugData get(Player p) { return p.getData(NanoAug.AUG_DATA.get()); }

    public boolean has(Aug a) { return installed.contains(a.id); }
    public boolean active(Aug a) { return active.contains(a.id); }
    public boolean any(Aug.Slot s) {
        for (Aug a : Aug.of(s)) if (has(a)) return true;
        return false;
    }
    public double potency() { return overclock > 0 ? 1.5 : 1.0; }
}
