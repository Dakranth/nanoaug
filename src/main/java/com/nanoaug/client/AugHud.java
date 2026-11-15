package com.nanoaug.client;

import com.nanoaug.Aug;
import com.nanoaug.AugData;
import com.nanoaug.Logic;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** HUD: cada elemento só aparece se o aprimoramento correspondente estiver instalado. */
public class AugHud {
    static final int AMBER = 0xFFFFC21A, ORANGE = 0xFFFF7A1A, RED = 0xFFFF4A3A, GRAY = 0xFF7A7A7A, GREEN = 0xFF58FF7A;
    /** tick (do jogo) do último uso de um aug: a lista de teclas aparece por alguns segundos. */
    public static long lastUse;

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui || mc.screen instanceof AugScreen) return;
        AugData d = AugData.get(p);
        if (d.levels.isEmpty()) return;

        Font f = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();

        // ---- barra de energia + lista de augs usáveis (esquerda embaixo)
        List<Aug> acts = new ArrayList<>();
        for (int i = 0; i < 4; i++) { Aug sa = d.slotAug(i); if (sa != null && d.has(sa)) acts.add(sa); }
        boolean recent = mc.level != null && mc.level.getGameTime() - lastUse < 100;
        boolean busy = d.overheat > 0 || d.overclock > 0 || !d.active.isEmpty() || d.energy < d.maxEnergy() - 0.5f;
        if (!acts.isEmpty() && (recent || busy)) {
            int x = 8;
            int rowsY = h - 14 - (recent ? acts.size() : 0) * 11;
            int barY = rowsY - 16;
            int barW = 90;
            boolean off = d.overheat > 0, oc = d.overclock > 0;
            int col = off ? RED : oc ? ORANGE : AMBER;
            g.drawString(f, Component.translatable(off ? "hud.nanoaug.offline" : oc ? "hud.nanoaug.overclock" : "hud.nanoaug.energy"),
                    x, barY - 10, col, true);
            g.fill(x - 1, barY - 1, x + barW + 1, barY + 7, 0xAA000000);
            float max = d.maxEnergy();
            int fw = off ? (int) (barW * (1f - d.overheat / 300f))
                    : oc ? (int) (barW * d.overclock / 300f)
                    : (int) (barW * Math.min(1f, d.energy / max));
            g.fill(x, barY, x + fw, barY + 6, col);
            for (int i = 1; i < 10; i++) g.fill(x + i * 9, barY, x + i * 9 + 1, barY + 6, 0x66000000);

            int y = rowsY;
            for (Aug a : recent ? acts : new ArrayList<Aug>()) {
                KeyMapping key = ClientSetup.SLOTS[d.slotOf(a)];
                int c = off ? RED : d.active(a) ? GREEN
                        : (!oc && a.mode == Aug.Mode.ACTION && d.energy < Logic.cost(a, d)) ? GRAY : AMBER;
                Component line = Component.literal("[").append(key.getTranslatedKeyMessage()).append("] ")
                        .append(Component.translatable(a.nameKey())).append(d.active(a) ? " ON" : "");
                g.drawString(f, line, x, y, c, true);
                y += 11;
            }
        }

        // ---- augs ligados (topo)
        int ty = 8;
        for (Aug a : Aug.values()) {
            if (d.active(a)) {
                g.drawCenteredString(f, Component.literal("[ ").append(Component.translatable(a.nameKey())).append(" ]"),
                        w / 2, ty, a == Aug.TITAN ? ORANGE : AMBER);
                ty += 10;
            }
        }

        // ---- Wayfinder Radar
        int wl = d.lvl(Aug.WAYFINDER);
        if (wl > 0 && d.active(Aug.SMART_VISION) && mc.level != null) {
            int r = 34, cx = w - r - 8, cy = r + 8;
            double range = wl >= 2 ? 32 : 20;
            g.fill(cx - r, cy - r, cx + r, cy + r, 0x88000000);
            g.renderOutline(cx - r, cy - r, r * 2, r * 2, AMBER);
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, AMBER);
            double yaw = Math.toRadians(p.getYRot());
            for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
                    e -> e != p && e.isAlive())) {
                double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
                double fwd = dz * Math.cos(yaw) - dx * Math.sin(yaw);
                double right = -(dx * Math.cos(yaw) + dz * Math.sin(yaw));
                if (fwd * fwd + right * right > range * range) continue;
                int px = cx + (int) (right / range * (r - 2));
                int py = cy - (int) (fwd / range * (r - 2));
                int c = e instanceof Enemy ? RED : e instanceof Player ? AMBER : GREEN;
                g.fill(px - 1, py - 1, px + 2, py + 2, c);
                if (wl >= 3) { // Vision Feedback: para onde cada um olha
                    double ye = Math.toRadians(e.getYRot());
                    double fx = -Math.sin(ye), fz = Math.cos(ye);
                    double ff = fz * Math.cos(yaw) - fx * Math.sin(yaw);
                    double fr = -(fx * Math.cos(yaw) + fz * Math.sin(yaw));
                    for (int i = 1; i <= 3; i++)
                        g.fill(px + (int) (fr * i * 2.5), py - (int) (ff * i * 2.5),
                                px + (int) (fr * i * 2.5) + 1, py - (int) (ff * i * 2.5) + 1, 0x99FF4A3A);
                }
            }
        }

        // ---- Smart Vision: ficha do alvo
        if (d.active(Aug.SMART_VISION)) {
            Entity t = mc.crosshairPickEntity;
            if (t instanceof LivingEntity le) {
                Component info = Component.empty().append(le.getName())
                        .append("  " + (int) Math.ceil(le.getHealth()) + "/" + (int) le.getMaxHealth());
                g.drawCenteredString(f, info, w / 2, h / 2 + 14, le instanceof Enemy ? RED : GREEN);
            }
        }

        // ---- CASIE
        if (d.has(Aug.SOCIAL) && mc.crosshairPickEntity instanceof Villager)
            g.drawCenteredString(f, Component.translatable("hud.nanoaug.casie"), w / 2, h / 2 + 26, AMBER);

        // ---- Icarus
        if (d.has(Aug.ICARUS_LANDING) && p.fallDistance > 3 && !p.getAbilities().flying)
            g.drawCenteredString(f, Component.translatable("hud.nanoaug.landing_ok"), w / 2, h / 2 + 38, AMBER);
    }
}
