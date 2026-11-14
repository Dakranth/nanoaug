package com.nanoaug;

import java.util.*;

public enum Aug {
    // CABEÇA
    SOCIAL("social", Slot.HEAD, 2, Mode.PASSIVE, 0f, 0),
    CLOAK("cloak", Slot.HEAD, 3, Mode.TOGGLE, 0.12f, 0),
    REMOTE_HACK("remote_hack", Slot.HEAD, 2, Mode.ACTION, 15f, 40),
    // OLHOS
    SMART_VISION("smart_vision", Slot.EYES, 2, Mode.TOGGLE, 0.04f, 0),
    // TORSO
    TITAN("titan", Slot.TORSO, 3, Mode.ACTION, 40f, 400),
    TYPHOON("typhoon", Slot.TORSO, 4, Mode.ACTION, 60f, 300),
    NANO_REPAIR("nano_repair", Slot.TORSO, 2, Mode.PASSIVE, 0f, 0),
    // BRAÇOS
    NANO_BLADE("nano_blade", Slot.ARMS, 3, Mode.PASSIVE, 0f, 0),
    STRENGTH("strength", Slot.ARMS, 2, Mode.PASSIVE, 0f, 0),
    TESLA("tesla", Slot.ARMS, 3, Mode.ACTION, 35f, 160),
    REFLEX("reflex", Slot.ARMS, 2, Mode.PASSIVE, 0f, 0),
    // PERNAS
    ICARUS_DASH("icarus_dash", Slot.LEGS, 2, Mode.ACTION, 25f, 40),
    ICARUS_LANDING("icarus_landing", Slot.LEGS, 2, Mode.PASSIVE, 0f, 0),
    JUMP("jump", Slot.LEGS, 2, Mode.PASSIVE, 0f, 0),
    SERVO("servo", Slot.LEGS, 2, Mode.PASSIVE, 0f, 0),
    // PELE
    DERMAL("dermal", Slot.SKIN, 2, Mode.PASSIVE, 0f, 0),
    BLAST_SKIN("blast_skin", Slot.SKIN, 3, Mode.PASSIVE, 0f, 0);

    public final String id;
    public final Slot slot;
    public final int cost;
    public final Mode mode;
    /** TOGGLE: gasto por tick. ACTION: custo por uso. */
    public final float energy;
    public final int cooldown;

    Aug(String id, Slot slot, int cost, Mode mode, float energy, int cooldown) {
        this.id = id; this.slot = slot; this.cost = cost; this.mode = mode; this.energy = energy; this.cooldown = cooldown;
    }

    public String nameKey() { return "aug." + NanoAug.MODID + "." + id; }
    public String descKey() { return nameKey() + ".desc"; }

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
        HEAD, EYES, TORSO, ARMS, LEGS, SKIN;
        public String key() { return "slot." + NanoAug.MODID + "." + name().toLowerCase(Locale.ROOT); }
    }

    public enum Mode { PASSIVE, TOGGLE, ACTION }
}
