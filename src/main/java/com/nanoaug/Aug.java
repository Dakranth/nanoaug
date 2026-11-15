package com.nanoaug;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Cada Aug é uma "família" com vários níveis (as melhorias do jogo). costs[i] = custo em Praxis do nível i+1. */
public enum Aug {
    // ---- CRÂNIO
    SOCIAL("social", Slot.HEAD, Mode.PASSIVE, null, 0, null, 2, 1),
    // ---- OLHOS
    SMART_VISION("smart_vision", Slot.EYES, Mode.TOGGLE, null, 0, null, 2, 0, 1, 1),
    MAGPIE("magpie", Slot.EYES, Mode.PASSIVE, "smart_vision", 2, null, 1),
    WAYFINDER("wayfinder", Slot.EYES, Mode.PASSIVE, "smart_vision", 1, null, 1, 1, 1),
    // ---- TORSO
    SENTINEL("sentinel", Slot.TORSO, Mode.PASSIVE, null, 0, null, 2, 1, 1),
    ANGIOGENESIS("angiogenesis", Slot.TORSO, Mode.PASSIVE, "sentinel", 1, null, 1, 1),
    REGEN_DELAY("regen_delay", Slot.TORSO, Mode.PASSIVE, "sentinel", 1, null, 1, 1),
    SARIF_RATE("sarif_rate", Slot.TORSO, Mode.PASSIVE, null, 0, null, 1, 1),
    SARIF_CAPACITY("sarif_capacity", Slot.TORSO, Mode.PASSIVE, null, 0, null, 1, 1),
    SARIF_DELAY("sarif_delay", Slot.TORSO, Mode.PASSIVE, null, 0, null, 1, 1),
    REBREATHER("rebreather", Slot.TORSO, Mode.PASSIVE, null, 0, null, 2, 0),
    // ---- BRAÇOS
    ARM("arm", Slot.ARMS, Mode.PASSIVE, null, 0, null, 2),
    PUNCH("punch", Slot.ARMS, Mode.ACTION, "arm", 1, null, 1),
    TAKEDOWN("takedown", Slot.ARMS, Mode.ACTION, "arm", 1, null, 0, 1),
    RELOAD("reload", Slot.ARMS, Mode.PASSIVE, "arm", 1, null, 1, 1),
    PEPS("peps", Slot.ARMS, Mode.ACTION, null, 0, null, 2, 1),
    REMOTE_HACK("remote_hack", Slot.ARMS, Mode.ACTION, null, 0, null, 2, 0, 1),
    TESLA("tesla", Slot.ARMS, Mode.ACTION, null, 0, null, 2, 1, 1),
    TESLA_RANGE("tesla_range", Slot.ARMS, Mode.PASSIVE, "tesla", 1, null, 1),
    NANOBLADE("nanoblade", Slot.ARMS, Mode.ACTION, null, 0, null, 2, 0, 1),
    // ---- COSTAS
    ICARUS_LANDING("icarus_landing", Slot.BACK, Mode.PASSIVE, null, 0, null, 2, 0),
    QUICKSILVER("quicksilver", Slot.BACK, Mode.PASSIVE, null, 0, null, 2, 0),
    TYPHOON("typhoon", Slot.BACK, Mode.ACTION, null, 0, null, 2),
    TYPHOON_LETHAL("typhoon_lethal", Slot.BACK, Mode.PASSIVE, "typhoon", 1, "typhoon_stun", 0),
    TYPHOON_STUN("typhoon_stun", Slot.BACK, Mode.PASSIVE, "typhoon", 1, "typhoon_lethal", 0),
    FOCUS("focus", Slot.BACK, Mode.TOGGLE, null, 0, null, 2, 0),
    ICARUS_DASH("icarus_dash", Slot.BACK, Mode.ACTION, null, 0, null, 2, 0, 1),
    // ---- PELE
    CLOAK("cloak", Slot.SKIN, Mode.TOGGLE, null, 0, null, 2, 1, 1),
    CLOAK_TAKEDOWN("cloak_takedown", Slot.SKIN, Mode.PASSIVE, "cloak", 1, null, 1),
    RHINO("rhino", Slot.SKIN, Mode.PASSIVE, null, 0, null, 2, 1, 1),
    EMP_SHIELD("emp_shield", Slot.SKIN, Mode.PASSIVE, "rhino", 2, null, 1),
    TITAN("titan", Slot.SKIN, Mode.TOGGLE, null, 0, null, 2, 1, 1),
    // ---- PERNAS
    LEG("leg", Slot.LEGS, Mode.PASSIVE, null, 0, null, 2),
    KLIPSPRINGER("klipspringer", Slot.LEGS, Mode.PASSIVE, "leg", 1, null, 0),
    SILENCERS("silencers", Slot.LEGS, Mode.PASSIVE, "klipspringer", 1, null, 1);

    public final String id;
    public final Slot slot;
    public final Mode mode;
    public final String requires;
    public final int reqLvl;
    public final String exclusive;
    public final int[] costs;

    Aug(String id, Slot slot, Mode mode, String requires, int reqLvl, String exclusive, int... costs) {
        this.id = id; this.slot = slot; this.mode = mode;
        this.requires = requires; this.reqLvl = reqLvl; this.exclusive = exclusive; this.costs = costs;
    }

    public int max() { return costs.length; }
    public String nameKey() { return "aug." + NanoAug.MODID + "." + id; }
    public String levelKey(int n) { return nameKey() + "." + n; }
    public String descKey(int n) { return levelKey(n) + ".desc"; }

    public Component lockReason(AugData d) {
        if (requires != null) {
            Aug r = byId(requires);
            if (r != null && d.lvl(r) < reqLvl)
                return Component.translatable("gui.nanoaug.requires", Component.translatable(r.nameKey()), reqLvl);
        }
        if (exclusive != null) {
            Aug x = byId(exclusive);
            if (x != null && d.lvl(x) > 0) return Component.translatable("gui.nanoaug.exclusive");
        }
        return null;
    }

    public boolean canUpgrade(AugData d) { return d.lvl(this) < max() && lockReason(d) == null; }
    public int nextCost(AugData d) { return costs[d.lvl(this)]; }

    public static Aug byId(String id) {
        for (Aug a : values()) if (a.id.equals(id)) return a;
        return null;
    }

    public static List<Aug> of(Slot s) {
        List<Aug> l = new ArrayList<>();
        for (Aug a : values()) if (a.slot == s) l.add(a);
        return l;
    }

    public enum Slot {
        HEAD, EYES, TORSO, ARMS, BACK, SKIN, LEGS;
        public String key() { return "slot." + NanoAug.MODID + "." + name().toLowerCase(Locale.ROOT); }
    }

    public enum Mode { PASSIVE, TOGGLE, ACTION }
}
