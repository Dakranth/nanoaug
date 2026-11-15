package com.nanoaug.client;

import com.nanoaug.Aug;
import com.nanoaug.AugData;
import com.nanoaug.NanoAug;
import com.nanoaug.Net;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;

@EventBusSubscriber(modid = NanoAug.MODID, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        AugData d = AugData.get(mc.player);

        while (ClientSetup.OPEN.consumeClick())
            if (mc.screen == null) mc.setScreen(new AugScreen());

        while (ClientSetup.OVERCLOCK.consumeClick())
            if (mc.screen == null) {
                AugHud.lastUse = mc.level.getGameTime();
                PacketDistributor.sendToServer(new Net.Request("overclock", (byte) 2));
            }

        for (Map.Entry<Aug, KeyMapping> en : ClientSetup.KEYS.entrySet()) {
            while (en.getValue().consumeClick())
                if (mc.screen == null && d.has(en.getKey())) {
                    AugHud.lastUse = mc.level.getGameTime();
                    PacketDistributor.sendToServer(new Net.Request(en.getKey().id, (byte) 1));
                }
        }
    }
}
