package com.nanoaug.client;

import com.nanoaug.Aug;
import com.nanoaug.AugData;
import net.minecraft.client.DeltaTracker;
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
    static final int GOLD = 0xFFF0B83A, CYAN = 0xFF35E0FF, RED = 0xFFFF4A3A, GRAY = 0xFF7A7A7A, GREEN = 0xFF58FF7A;

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui || mc.screen instanceof AugScreen) return;
        AugData d = AugData.get(p);
        if (d.installed.isEmpty()) return;

        Font f = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();

        // ---- barra de energia + lista de aprimoramentos ativáveis (esquerda embaixo)
        List<Aug> acts = new ArrayList<>();
        for (Aug a : Aug.values()) if (d.has(a) && a.mode != Aug.Mode.PASSIVE) acts.add(a);
        if (!acts.isEmpty()) {
            int x = 8;
            int rowsY = h - 14 - acts.size() * 11;
            int barY = rowsY - 16;
            int barW = 90;
            boolean off = d.overheat > 0, oc = d.overclock > 0;
            int col = off ? RED : oc ? GOLD : CYAN;
            Component label = Component.translatable(off ? "hud.nanoaug.offline" : oc ? "hud.nanoaug.overclock" : "hud.nanoaug.energy");
            g.drawString(f, label, x, barY - 10, col, true);
            g.fill(x - 1, barY - 1, x + barW + 1, barY + 7, 0xAA000000);
            int fw = off ? (int) (barW * (1f - d.overheat / 300f))
                    : oc ? (int) (barW * d.overclock / 300f)
                    : (int) (barW * d.energy / AugData.MAX_ENERGY);
            g.fill(x, barY, x + fw, barY + 6, col);
            for (int i = 1; i < 10; i++) g.fill(x + i * 9, barY, x + i * 9 + 1, barY + 6, 0x66000000);

            int y = rowsY;
            for (Aug a : acts) {
                var key = ClientSetup.KEYS.get(a);
                int c = off ? RED : d.active(a) ? GREEN
                        : (!oc && d.energy < a.energy && a.mode == Aug.Mode.ACTION) ? GRAY : GOLD;
                Component line = Component.literal("[").append(key.getTranslatedKeyMessage()).append("] ")
                        .append(Component.translatable(a.nameKey()))
                        .append(d.active(a) ? " ON" : "");
                g.drawString(f, line, x, y, c, true);
                y += 11;
            }
        }

        // ---- Cloak: aviso no topo
        if (d.active(Aug.CLOAK))
            g.drawCenteredString(f, Component.translatable("hud.nanoaug.cloak"), w / 2, 10, CYAN);

        // ---- Visão inteligente: radar + ficha do alvo
        if (d.active(Aug.SMART_VISION) && mc.level != null) {
            int r = 34, cx = w - r - 8, cy = r + 8;
            g.fill(cx - r, cy - r, cx + r, cy + r, 0x88000000);
            g.renderOutline(cx - r, cy - r, r * 2, r * 2, CYAN);
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, GOLD);
            double yaw = Math.toRadians(p.getYRot());
            double range = 24;
            for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
                    e -> e != p && e.isAlive())) {
                double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
                double fwd = dz * Math.cos(yaw) - dx * Math.sin(yaw);
                double right = -(dx * Math.cos(yaw) + dz * Math.sin(yaw));
                if (fwd * fwd + right * right > range * range) continue;
                int px = cx + (int) (right / range * (r - 2));
                int py = cy - (int) (fwd / range * (r - 2));
                int c = e instanceof Enemy ? RED : e instanceof Player ? CYAN : GREEN;
                g.fill(px - 1, py - 1, px + 2, py + 2, c);
            }
            Entity t = mc.crosshairPickEntity;
            if (t instanceof LivingEntity le) {
                Component info = Component.empty().append(le.getName())
                        .append("  " + (int) Math.ceil(le.getHealth()) + "/" + (int) le.getMaxHealth());
                g.drawCenteredString(f, info, w / 2, h / 2 + 14, le instanceof Enemy ? RED : GREEN);
            }
        }

        // ---- CASIE: aviso ao olhar para aldeão
        if (d.has(Aug.SOCIAL) && mc.crosshairPickEntity instanceof Villager)
            g.drawCenteredString(f, Component.translatable("hud.nanoaug.casie"), w / 2, h / 2 + 26, GOLD);

        // ---- Icarus: indicador de queda
        if (d.has(Aug.ICARUS_LANDING) && p.fallDistance > 3 && !p.getAbilities().flying)
            g.drawCenteredString(f, Component.translatable(d.energy >= 10 || d.overclock > 0
                    ? "hud.nanoaug.landing_ok" : "hud.nanoaug.landing_fail"), w / 2, h / 2 + 38,
                    d.energy >= 10 || d.overclock > 0 ? CYAN : RED);
    }
}
