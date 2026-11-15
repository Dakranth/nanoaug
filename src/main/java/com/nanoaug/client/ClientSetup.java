package com.nanoaug.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nanoaug.NanoAug;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;


@EventBusSubscriber(modid = NanoAug.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    private static final String CAT = "key.categories.nanoaug";

    public static final KeyMapping OPEN = new KeyMapping("key.nanoaug.open", InputConstants.KEY_U, CAT);
    public static final KeyMapping OVERCLOCK = new KeyMapping("key.nanoaug.overclock", InputConstants.KEY_O, CAT);
    /** 4 slots de uso: Z X C V por padrão. */
    public static final KeyMapping[] SLOTS = {
            new KeyMapping("key.nanoaug.slot1", InputConstants.KEY_Z, CAT),
            new KeyMapping("key.nanoaug.slot2", InputConstants.KEY_X, CAT),
            new KeyMapping("key.nanoaug.slot3", InputConstants.KEY_C, CAT),
            new KeyMapping("key.nanoaug.slot4", InputConstants.KEY_V, CAT)
    };

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(OPEN);
        e.register(OVERCLOCK);
        for (KeyMapping k : SLOTS) e.register(k);
    }

    @SubscribeEvent
    public static void guiLayers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(NanoAug.MODID, "hud"), AugHud::render);
    }

    @SubscribeEvent
    public static void layerDefs(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(AugLayer.LOC, AugLayer::createLayer);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (PlayerSkin.Model m : e.getSkins()) {
            EntityRenderer<?> r = e.getSkin(m);
            if (r instanceof PlayerRenderer pr) pr.addLayer(new AugLayer(pr, e.getEntityModels()));
        }
    }
}
