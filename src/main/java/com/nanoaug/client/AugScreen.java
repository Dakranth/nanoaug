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
    static final int GOLD = 0xFFF0B83A, AMBER = 0xFFFFC21A, GRAY = 0xFF7A7A7A, RED = 0xFFFF4A3A,
            GREEN = 0xFF58FF7A, TXT = 0xFFD8D8D8, DIM = 0xFFA8A8A8;
    private static final int VIS = 5, ROW = 20, PW = 180, PH = 218;

    private Aug.Slot slot = Aug.Slot.HEAD;
    private Aug sel = Aug.of(Aug.Slot.HEAD).get(0);
    private int scroll = 0;

    public AugScreen() { super(Component.translatable("gui.nanoaug.title")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0xEE04080C, 0xEE120D03);
        for (int y = 0; y < height; y += 3) g.fill(0, y, width, y + 1, 0x0CFFFFFF);
    }

    private int bx() { return width / 2 - 90; }
    private int cy() { return height / 2; }
    private int px() { return bx() + 112; }
    private int py() { return cy() - 107; }

    private int[] node(Aug.Slot s) {
        int bx = bx(), cy = cy();
        return switch (s) {
            case EYES -> new int[]{bx - 100, cy - 85};
            case HEAD -> new int[]{bx + 52, cy - 85};
            case ARMS -> new int[]{bx - 100, cy - 45};
            case TORSO -> new int[]{bx + 52, cy - 45};
            case SKIN -> new int[]{bx - 100, cy - 5};
            case BACK -> new int[]{bx + 52, cy - 5};
            case LEGS -> new int[]{bx - 100, cy + 35};
        };
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int para(GuiGraphics g, Component c, int x, int y, int w, int color, int maxY) {
        for (FormattedCharSequence l : font.split(c, w)) {
            if (y > maxY) break;
            g.drawString(font, l, x, y, color, false);
            y += 9;
        }
        return y;
    }

    private Component upgradeLabel(AugData d) {
        int l = d.lvl(sel);
        if (l >= sel.max()) return Component.translatable("gui.nanoaug.maxed");
        if (sel.lockReason(d) != null) return Component.translatable("gui.nanoaug.locked_btn");
        int c = sel.nextCost(d);
        String base = l == 0 ? "install" : "upgrade";
        return c == 0 ? Component.translatable("gui.nanoaug." + base + "_free")
                : Component.translatable("gui.nanoaug." + base + "_n", c);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        AugData d = AugData.get(p);
        int bx = bx(), cy = cy();

        g.drawString(font, title, 12, 10, GOLD, true);
        g.drawString(font, Component.translatable("gui.nanoaug.praxis", d.praxis), 12, 22, AMBER, true);
        g.drawString(font, Component.translatable("gui.nanoaug.energy", (int) d.energy, (int) d.maxEnergy()), 12, 34,
                d.overheat > 0 ? RED : AMBER, true);
        g.drawString(font, Component.translatable("gui.nanoaug.overclock_hint",
                ClientSetup.OVERCLOCK.getTranslatedKeyMessage()), 12, height - 12, GRAY, true);

        // corpo
        g.renderOutline(bx - 30, cy - 100, 60, 175, 0x66F0B83A);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, bx - 28, cy - 98, bx + 28, cy + 72, 68, 0.0625F, mx, my, p);

        // regiões
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
        for (int i = scroll; i < Math.min(list.size(), scroll + VIS); i++) {
            Aug a = list.get(i);
            int ry = py + 18 + (i - scroll) * ROW;
            int l = d.lvl(a);
            g.fill(px + 4, ry, px + PW - 4, ry + ROW - 2, a == sel ? 0xAA3A2E08 : 0x66000000);
            g.drawString(font, Component.translatable(a.nameKey()), px + 8, ry + 5, l > 0 ? GOLD : GRAY, true);
            Component right;
            int rc;
            if (l == 0 && a.lockReason(d) != null) { right = Component.translatable("gui.nanoaug.locked"); rc = RED; }
            else { right = Component.literal(l + "/" + a.max()); rc = l >= a.max() ? GREEN : l > 0 ? AMBER : GRAY; }
            g.drawString(font, right, px + PW - 8 - font.width(right), ry + 5, rc, true);
        }
        if (list.size() > VIS) {
            g.drawString(font, "^", px + PW - 12, py + 6, scroll > 0 ? AMBER : 0xFF444444, false);
            g.drawString(font, "v", px + PW - 22, py + 6, scroll < list.size() - VIS ? AMBER : 0xFF444444, false);
        }

        // detalhes
        int dx = px + 8, dw = PW - 16, dy = py + 18 + VIS * ROW + 4, maxY = py + PH - 30;
        int l = d.lvl(sel);
        dy = para(g, Component.translatable(sel.nameKey()), dx, dy, dw, AMBER, maxY);
        if (l > 0) {
            dy = para(g, Component.translatable("gui.nanoaug.level", l, sel.max()).append(": ")
                    .append(Component.translatable(sel.levelKey(l))), dx, dy, dw, GREEN, maxY);
            dy = para(g, Component.translatable(sel.descKey(l)), dx, dy, dw, TXT, maxY);
        }
        if (l < sel.max()) {
            Component lock = sel.lockReason(d);
            if (lock != null) para(g, lock, dx, dy, dw, RED, maxY);
            else {
                dy = para(g, Component.translatable("gui.nanoaug.next").append(": ")
                        .append(Component.translatable(sel.levelKey(l + 1))), dx, dy, dw, GOLD, maxY);
                para(g, Component.translatable(sel.descKey(l + 1)), dx, dy, dw, DIM, maxY);
            }
        }

        // botões
        int bY = py + PH - 20;
        boolean useBtn = false;
        int w1 = useBtn ? (PW - 20) / 2 : PW - 16;
        boolean canUp = sel.canUpgrade(d) && d.praxis >= sel.nextCost(d);
        boolean hov1 = canUp && in(mx, my, px + 8, bY, w1, 14);
        g.fill(px + 8, bY, px + 8 + w1, bY + 14, hov1 ? 0xFF6B5410 : 0xAA1A1405);
        g.renderOutline(px + 8, bY, w1, 14, canUp ? GOLD : 0xFF444444);
        g.drawCenteredString(font, upgradeLabel(d), px + 8 + w1 / 2, bY + 3, canUp ? GOLD : GRAY);
        // ---- 4 slots de uso (Z X C V)
        for (int i = 0; i < 4; i++) {
            int sx = bx - 100 + i * 52, sy = cy + 82;
            Aug sa = d.slotAug(i);
            boolean hov = in(mx, my, sx, sy, 48, 22);
            g.fill(sx, sy, sx + 48, sy + 22, hov ? 0xCC3A2E08 : 0xAA0A0A0A);
            g.renderOutline(sx, sy, 48, 22, sa != null ? GOLD : 0xFF555555);
            g.drawString(font, "[" + ClientSetup.SLOTS[i].getTranslatedKeyMessage().getString() + "]", sx + 3, sy + 2, AMBER, false);
            if (sa != null)
                g.drawString(font, font.plainSubstrByWidth(Component.translatable(sa.nameKey()).getString(), 44), sx + 3, sy + 12, GOLD, false);
        }
        g.drawString(font, Component.translatable("gui.nanoaug.slots_hint"), bx - 100, cy + 108, GRAY, false);
        for (int i = 0; i < 4; i++) {
            Aug sa = d.slotAug(i);
            if (sa != null && in(mx, my, bx - 100 + i * 52, cy + 82, 48, 22))
                g.renderTooltip(font, Component.translatable(sa.nameKey()), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        Player p = Minecraft.getInstance().player;
        if (p == null) return false;
        AugData d = AugData.get(p);

        for (int i = 0; i < 4; i++) {
            if (in(mx, my, bx() - 100 + i * 52, cy() + 82, 48, 22)) {
                Aug cur = d.slotAug(i);
                if (btn == 1) {
                    PacketDistributor.sendToServer(new Net.Request(i + ":", (byte) 3));
                } else if (sel.mode != Aug.Mode.PASSIVE && d.has(sel)) {
                    PacketDistributor.sendToServer(new Net.Request(i + ":" + sel.id, (byte) 3));
                } else if (cur != null) {
                    sel = cur;
                    slot = cur.slot;
                    scroll = 0;
                }
                return true;
            }
        }
        for (Aug.Slot s : Aug.Slot.values()) {
            int[] n = node(s);
            if (in(mx, my, n[0], n[1], 48, 16)) {
                slot = s;
                sel = Aug.of(s).get(0);
                scroll = 0;
                return true;
            }
        }
        int px = px(), py = py();
        List<Aug> list = Aug.of(slot);
        for (int i = scroll; i < Math.min(list.size(), scroll + VIS); i++) {
            if (in(mx, my, px + 4, py + 18 + (i - scroll) * ROW, PW - 8, ROW - 2)) {
                sel = list.get(i);
                return true;
            }
        }
        int bY = py + PH - 20;
        boolean useBtn = false;
        int w1 = useBtn ? (PW - 20) / 2 : PW - 16;
        if (in(mx, my, px + 8, bY, w1, 14)) {
            if (sel.canUpgrade(d) && d.praxis >= sel.nextCost(d))
                PacketDistributor.sendToServer(new Net.Request(sel.id, (byte) 0));
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int n = Aug.of(slot).size();
        if (n > VIS && mx >= px()) {
            scroll = Math.max(0, Math.min(n - VIS, scroll - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientSetup.OPEN.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
