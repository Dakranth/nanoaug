package com.nanoaug.client;

import com.nanoaug.AugData;
import com.nanoaug.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class ClientHooks {
    /** visual das augs de outro jogador (só o que o render precisa). */
    public static void publicSync(Net.PublicSync s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || s.id().equals(mc.player.getUUID())) return;
        Player other = mc.level.getPlayerByUUID(s.id());
        if (other == null) return;
        AugData d = AugData.get(other);
        d.levels.clear();
        d.levels.putAll(s.levels());
        d.active.clear();
        d.active.addAll(s.active());
        d.overclock = s.overclock();
    }

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
        d.stable = s.stable();
        d.glitch = s.glitch();
        d.standby.clear();
        d.standby.addAll(s.standby());
        d.calib = s.calib();
        for (int i = 0; i < 4; i++) d.slots[i] = i < s.slots().size() ? s.slots().get(i) : "";
    }
}
