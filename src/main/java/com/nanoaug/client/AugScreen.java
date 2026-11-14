package com.nanoaug.client;

import com.nanoaug.Aug;
import com.nanoaug.AugData;
import com.nanoaug.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class AugScreen extends Screen {
    static final int GOLD = 0xFFF0B83A, CYAN = 0xFF35E0FF, GRAY = 0xFF7A7A7A, RED = 0xFFFF4A3A, GREEN = 0xFF58FF7A;
    private Aug.Slot slot = Aug.Slot.HEAD;
    private Aug sel = Aug.of(Aug.Slot.HEAD).get(0);

    public AugScreen() { super(Component.translatable("gui.nanoaug.title")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0xEE04080C, 0xEE120D03);
        for (int y = 0; y < height; y += 3) g.fill(0, y, width, y + 1, 0x0CFFFFFF);
    }

    private int bx() { return width / 2 - 90; }
    private int cy() { return height / 2; }

    private int[] node(Aug.Slot s) {
        int bx = bx(), cy = cy();
        return switch (s) {
            case EYES -> new int[]{bx - 100, cy - 80};
            case HEAD -> new int[]{bx + 52, cy - 80};
            case ARMS -> new int[]{bx - 100, cy - 25};
            case TORSO -> new int[]{bx + 52, cy - 25};
            case SKIN -> new int[]{bx - 100, cy + 30};
            case LEGS -> new int[]{bx + 52, cy + 30};
        };
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // painel direito
    private int px() { return bx() + 112; }
    private int py() { return cy() - 100; }
    private static final int PW = 170, PH = 190;

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        AugData d = AugData.get(p);
        int bx = bx(), cy = cy();

        g.drawString(font, title, 12, 10, GOLD, true);
        g.drawString(font, Component.translatable("gui.nanoaug.praxis", d.praxis), 12, 22, CYAN, true);
        g.drawString(font, Component.translatable("gui.nanoaug.energy", (int) d.energy), 12, 34,
                d.overheat > 0 ? RED : CYAN, true);
        g.drawString(font, Component.translatable("gui.nanoaug.overclock_hint",
                ClientSetup.OVERCLOCK.getTranslatedKeyMessage()), 12, 46, GRAY, true);

        // corpo
        g.renderOutline(bx - 30, cy - 100, 60, 175, 0x66F0B83A);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, bx - 28, cy - 98, bx + 28, cy + 72, 68, 0.0625F, mx, my, p);

        // nós
        for (Aug.Slot s : Aug.Slot.values()) {
            int[] n = node(s);
            boolean selected = s == slot, left = n[0] < bx;
            int lineY = n[1] + 8;
            int lc = selected ? GOLD : 0xFF555555;
            if (left) g.hLine(n[0] + 48, bx - 22, lineY, lc); else g.hLine(bx + 22, n[0], lineY, lc);
            g.fill(left ? bx - 23 : bx + 21, lineY - 1, left ? bx - 20 : bx + 24, lineY + 2, lc);
            g.fill(n[0], n[1], n[0] + 48, n[1] + 16, selected ? 0xCC3A2E08 : 0xAA0A0A0A);
            g.renderOutline(n[0], n[1], 48, 16, lc);
            g.drawCenteredString(font, Component.translatable(s.key()), n[0] + 24, n[1] + 4, d.any(s) ? GOLD : GRAY);
        }

        // painel
        int px = px(), py = py();
        g.fill(px, py, px + PW, py + PH, 0xAA050505);
        g.renderOutline(px, py, PW, PH, GOLD);
        g.drawString(font, Component.translatable(slot.key()), px + 8, py + 6, GOLD, true);

        List<Aug> list = Aug.of(slot);
        for (int i = 0; i < list.size(); i++) {
            Aug a = list.get(i);
            int ry = py + 20 + i * 22;
            boolean inst = d.has(a);
            g.fill(px + 4, ry, px + PW - 4, ry + 19, a == sel ? 0xAA3A2E08 : 0x66000000);
            g.drawString(font, Component.translatable(a.nameKey()), px + 8, ry + 6, inst ? GOLD : GRAY, true);
            Component right = inst
                    ? Component.translatable(d.active(a) ? "gui.nanoaug.on" : "gui.nanoaug.installed")
                    : Component.translatable("gui.nanoaug.cost", a.cost);
            g.drawString(font, right, px + PW - 8 - font.width(right), ry + 6,
                    inst ? (d.active(a) ? GREEN : CYAN) : (d.praxis >= a.cost ? GOLD : RED), true);
        }

        // descrição
        int dy = py + 20 + 4 * 22 + 4;
        for (FormattedCharSequence line : font.split(Component.translatable(sel.descKey()), PW - 16)) {
            g.drawString(font, line, px + 8, dy, 0xFFD8D8D8, false);
            dy += 10;
        }

        // botão
        int bY = py + PH - 22;
        boolean inst = d.has(sel);
        Component label;
        boolean enabled = true;
        if (!inst) {
            label = Component.translatable("gui.nanoaug.install", sel.cost);
            enabled = d.praxis >= sel.cost;
        } else if (sel.mode == Aug.Mode.PASSIVE) {
            label = Component.translatable("gui.nanoaug.passive");
            enabled = false;
        } else if (sel.mode == Aug.Mode.TOGGLE) {
            label = Component.translatable(d.active(sel) ? "gui.nanoaug.turn_off" : "gui.nanoaug.turn_on");
        } else {
            label = Component.translatable("gui.nanoaug.use", ClientSetup.KEYS.get(sel).getTranslatedKeyMessage());
        }
        boolean hover = enabled && in(mx, my, px + 8, bY, PW - 16, 14);
        g.fill(px + 8, bY, px + PW - 8, bY + 14, hover ? 0xFF6B5410 : 0xAA1A1405);
        g.renderOutline(px + 8, bY, PW - 16, 14, enabled ? GOLD : 0xFF444444);
        g.drawCenteredString(font, label, px + PW / 2, bY + 3, enabled ? GOLD : GRAY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        Player p = Minecraft.getInstance().player;
        if (p == null) return false;
        AugData d = AugData.get(p);

        for (Aug.Slot s : Aug.Slot.values()) {
            int[] n = node(s);
            if (in(mx, my, n[0], n[1], 48, 16)) {
                slot = s;
                sel = Aug.of(s).get(0);
                return true;
            }
        }
        int px = px(), py = py();
        List<Aug> list = Aug.of(slot);
        for (int i = 0; i < list.size(); i++) {
            if (in(mx, my, px + 4, py + 20 + i * 22, PW - 8, 19)) {
                sel = list.get(i);
                return true;
            }
        }
        if (in(mx, my, px + 8, py + PH - 22, PW - 16, 14)) {
            if (!d.has(sel)) {
                if (d.praxis >= sel.cost) PacketDistributor.sendToServer(new Net.Request(sel.id, (byte) 0));
            } else if (sel.mode != Aug.Mode.PASSIVE) {
                PacketDistributor.sendToServer(new Net.Request(sel.id, (byte) 1));
            }
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientSetup.OPEN.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
