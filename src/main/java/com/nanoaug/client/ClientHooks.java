package com.nanoaug.client;

import com.nanoaug.AugData;
import com.nanoaug.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class ClientHooks {
    public static void sync(Net.Sync s) {
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        AugData d = AugData.get(p);
        d.praxis = s.praxis();
        d.levels.clear();
        d.levels.putAll(s.levels());
        d.active.clear();
        d.active.addAll(s.active());
        d.energy = s.energy();
        d.overclock = s.overclock();
        d.overheat = s.overheat();
        for (int i = 0; i < 4; i++) d.slots[i] = i < s.slots().size() ? s.slots().get(i) : "";
    }
}
