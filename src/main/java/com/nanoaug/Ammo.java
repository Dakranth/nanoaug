package com.nanoaug;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Munição dos augs de arma (TESLA, Nanoblade, Typhoon). */
public final class Ammo {
    private Ammo() {}

    public static Item item(Aug a) {
        return switch (a) {
            case TESLA -> NanoAug.TESLA_CARTRIDGE.get();
            case NANOBLADE -> NanoAug.BLADE_AMMO.get();
            case TYPHOON -> NanoAug.TYPHOON_AMMO.get();
            default -> null;
        };
    }

    public static int count(Player p, Aug a) {
        Item it = item(a);
        if (it == null) return -1;
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(it)) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (s.is(it)) n += s.getCount();
        return n;
    }

    public static void consume(Player p, Aug a) {
        Item it = item(a);
        if (it == null) return;
        for (ItemStack s : p.getInventory().items) if (s.is(it)) { s.shrink(1); return; }
        for (ItemStack s : p.getInventory().offhand) if (s.is(it)) { s.shrink(1); return; }
    }
}
