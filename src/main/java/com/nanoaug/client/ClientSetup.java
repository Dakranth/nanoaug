package com.nanoaug.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nanoaug.Aug;
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

import java.util.EnumMap;
import java.util.Map;

@EventBusSubscriber(modid = NanoAug.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    private static final String CAT = "key.categories.nanoaug";

    public static final KeyMapping OPEN = new KeyMapping("key.nanoaug.open", InputConstants.KEY_U, CAT);
    public static final KeyMapping OVERCLOCK = new KeyMapping("key.nanoaug.overclock", InputConstants.KEY_O, CAT);
    public static final Map<Aug, KeyMapping> KEYS = new EnumMap<>(Aug.class);

    static {
        KEYS.put(Aug.SMART_VISION, new KeyMapping("key.nanoaug.smart_vision", InputConstants.KEY_B, CAT));
        KEYS.put(Aug.CLOAK, new KeyMapping("key.nanoaug.cloak", InputConstants.KEY_C, CAT));
        KEYS.put(Aug.TITAN, new KeyMapping("key.nanoaug.titan", InputConstants.KEY_H, CAT));
        KEYS.put(Aug.FOCUS, new KeyMapping("key.nanoaug.focus", InputConstants.KEY_J, CAT));
        KEYS.put(Aug.PUNCH, new KeyMapping("key.nanoaug.punch", InputConstants.KEY_K, CAT));
        KEYS.put(Aug.TAKEDOWN, new KeyMapping("key.nanoaug.takedown", InputConstants.KEY_Z, CAT));
        KEYS.put(Aug.PEPS, new KeyMapping("key.nanoaug.peps", InputConstants.KEY_Y, CAT));
        KEYS.put(Aug.REMOTE_HACK, new KeyMapping("key.nanoaug.remote_hack", InputConstants.KEY_X, CAT));
        KEYS.put(Aug.TESLA, new KeyMapping("key.nanoaug.tesla", InputConstants.KEY_R, CAT));
        KEYS.put(Aug.NANOBLADE, new KeyMapping("key.nanoaug.nanoblade", InputConstants.KEY_N, CAT));
        KEYS.put(Aug.TYPHOON, new KeyMapping("key.nanoaug.typhoon", InputConstants.KEY_G, CAT));
        KEYS.put(Aug.ICARUS_DASH, new KeyMapping("key.nanoaug.icarus_dash", InputConstants.KEY_V, CAT));
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(OPEN);
        e.register(OVERCLOCK);
        KEYS.values().forEach(e::register);
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
