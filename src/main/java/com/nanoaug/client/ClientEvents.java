package com.nanoaug.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nanoaug.Aug;
import com.nanoaug.AugData;
import com.nanoaug.DashAim;
import com.nanoaug.NanoAug;
import com.nanoaug.Net;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NanoAug.MODID, value = Dist.CLIENT)
public class ClientEvents {
    private static final boolean[] WAS_DOWN = new boolean[4];
    private static int aimSlot = -1;
    private static long aimStart;
    /** destino do Icarus Dash mirado (null = não está mirando). */
    public static Vec3 aimTarget;

    private static void use(Aug a) {
        PacketDistributor.sendToServer(new Net.Request(a.id, (byte) 1));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { aimSlot = -1; aimTarget = null; return; }
        AugData d = AugData.get(mc.player);
        long now = mc.level.getGameTime();

        while (ClientSetup.OPEN.consumeClick())
            if (mc.screen == null) mc.setScreen(new AugScreen());

        while (ClientSetup.OVERCLOCK.consumeClick())
            if (mc.screen == null) {
                AugHud.lastUse = now;
                PacketDistributor.sendToServer(new Net.Request("overclock", (byte) 2));
            }

        for (int i = 0; i < 4; i++) {
            KeyMapping k = ClientSetup.SLOTS[i];
            while (k.consumeClick()) { /* limpa cliques acumulados */ }
            boolean down = k.isDown() && mc.screen == null;
            Aug a = d.slotAug(i);

            if (down && !WAS_DOWN[i] && a != null && d.has(a)) {
                AugHud.lastUse = now;
                if (a == Aug.ICARUS_DASH) { aimSlot = i; aimStart = now; } // segurar = mirar
                else use(a);
            }
            if (!down && WAS_DOWN[i] && aimSlot == i) {
                boolean cancel = mc.screen != null;
                long held = now - aimStart;
                aimSlot = -1;
                aimTarget = null;
                if (!cancel) {
                    if (held < 6) use(Aug.ICARUS_DASH); // toque rápido = dash normal
                    else PacketDistributor.sendToServer(new Net.Request(Aug.ICARUS_DASH.id, (byte) 5));
                }
            }
            WAS_DOWN[i] = down;
        }

        if (aimSlot >= 0 && now - aimStart >= 6)
            aimTarget = DashAim.compute(mc.player, DashAim.range(d.lvl(Aug.ICARUS_DASH)) * d.potency());
        else aimTarget = null;
    }

    /** Remote Hacking (passivo): botão direito de mão vazia mirando alavanca, porta etc. longe. */
    @SubscribeEvent
    public static void rightClickEmpty(PlayerInteractEvent.RightClickEmpty e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (AugData.get(mc.player).has(Aug.REMOTE_HACK))
            PacketDistributor.sendToServer(new Net.Request("remote_hack", (byte) 6));
    }

    /** Marcador amarelo do destino do dash. */
    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || aimTarget == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        VertexConsumer vc = bs.getBuffer(RenderType.lines());
        AABB box = mc.player.getBoundingBox().move(aimTarget.subtract(mc.player.position()));
        LevelRenderer.renderLineBox(ps, vc, box, 1f, 0.78f, 0.1f, 1f);
        LevelRenderer.renderLineBox(ps, vc, box.deflate(0.18), 1f, 0.9f, 0.4f, 1f);
        LevelRenderer.renderLineBox(ps, vc, new AABB(aimTarget.x - 0.03, aimTarget.y, aimTarget.z - 0.03,
                aimTarget.x + 0.03, aimTarget.y + 2.6, aimTarget.z + 0.03), 1f, 0.78f, 0.1f, 0.8f);
        ps.popPose();
        bs.endBatch(RenderType.lines());
    }
}
