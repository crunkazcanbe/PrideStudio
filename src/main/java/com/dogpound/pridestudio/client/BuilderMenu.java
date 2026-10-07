package com.dogpound.pridestudio.client;

import com.dogpound.pridestudio.PrideFrame;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.world.GameType;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The builder menu (hold Left Alt, her Axiom reference frame892), in the Pride look: Studio + Edit Block on top,
 * game-mode buttons, saved hotbars in the middle (endless pages), builder switches down the left, flight speed down
 * the right, settings + clear at the bottom right. No dark background — you still see the world.
 */
public class BuilderMenu extends GuiScreen {
    private final List<Object[]> hits = new ArrayList<>();
    private String hint = "";
    private int page = 0, selected = -1;          // selected = saved row picked with the wheel (loads when you let go of Alt)
    private ItemStack carried = ItemStack.EMPTY;  // item picked up with a click, moving between saved slots
    private int carriedFrom = -1;
    private int sliderX, sliderY, sliderH;
    private boolean dragging;
    private long openedAt = System.currentTimeMillis();
    private boolean clearArmed;

    static final int S = 20;                      // slot size
    private final boolean sticky;                 // opened by /ps menu: stays open until Esc (no Alt held)

    public BuilderMenu() { this(false); }
    public BuilderMenu(boolean sticky) { this.sticky = sticky; }

    @Override public boolean doesGuiPauseGame() { return false; }

    private float k = 1F;

    /** big screens: grow the menu so it fills about 60% of the height (her 10240x2632 window made it tiny) */
    static float menuScale(int h) {
        if (ClientState.S.menuScale > 0) return ClientState.S.menuScale;
        float natural = Hotbars.ROWS * S + S + 3 * 22 + 40;
        return Math.max(1F, Math.min(4F, Math.round(h * 0.6F / natural * 4F) / 4F));
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        k = menuScale(height);
        int rw = width, rh = height;
        width = (int) (rw / k); height = (int) (rh / k);
        GlStateManager.pushMatrix();
        GlStateManager.scale(k, k, 1F);
        try { drawScaled((int) (mx / k), (int) (my / k)); } finally { GlStateManager.popMatrix(); width = rw; height = rh; }
    }

    private void drawScaled(int mx, int my) {
        hits.clear(); hint = "";
        int gw = 9 * S, gx = (width - gw) / 2;
        int gridH = Hotbars.ROWS * S + 6 + S;                 // saved rows, gap, live hotbar
        int top = Math.max(4, height - 22 - gridH - 3 * 22 - 8);
        int x0 = gx - 6, w0 = gw + 12;

        // panel behind everything (translucent so the world shows)
        int px = gx - 34, py = top - 8, pw = gw + 34 + 56, ph = height - 6 - py;
        PrideFrame.gradient(px, py, px + pw, py + ph, 0xC8140E22, 0xC8080510);
        for (int i = 0; i < PrideFrame.RAINBOW.length; i++) { int sw = pw / PrideFrame.RAINBOW.length; Gui.drawRect(px + i * sw, py, i == PrideFrame.RAINBOW.length - 1 ? px + pw : px + (i + 1) * sw, py + 2, PrideFrame.RAINBOW[i]); }

        // top buttons
        int y = top;
        btn(mx, my, x0, y, w0, 18, "✦ Pride Studio", 0xFF3D2168, () -> mc.displayGuiScreen(new StudioScreen(null)), "Open the full Pride Studio panel (\\)"); y += 21;
        btn(mx, my, x0, y, w0, 18, "✈ Editor Mode", 0xFF6A3FA0, EditorMode::enter, "Fly the camera out and brush the land under the mouse, like Axiom / Blender (` key)"); y += 21;
        btn(mx, my, x0, y, w0, 18, "Edit Block", 0xFF2A2238, () -> mc.displayGuiScreen(new BlockEditScreen(this)), "Change the facing, colour, half… of the block you're looking at"); y += 21;
        // game modes (the ones you're not in)
        List<Object[]> modes = new ArrayList<>();
        for (GameType g : new GameType[]{GameType.CREATIVE, GameType.SURVIVAL, GameType.SPECTATOR, GameType.ADVENTURE})
            if (mc.playerController.getCurrentGameType() != g) modes.add(new Object[]{ g });
        int mw = (w0 - (modes.size() - 1) * 3) / modes.size();
        for (int i = 0; i < modes.size(); i++) {
            GameType g = (GameType) modes.get(i)[0];
            String name = g.getName().substring(0, 1).toUpperCase() + g.getName().substring(1);
            btn(mx, my, x0 + i * (mw + 3), y, mw, 18, name, 0xFF24408E, () -> { mc.player.sendChatMessage("/gamemode " + g.getName()); mc.displayGuiScreen(null); }, "Switch to " + name);
        }
        y += 24;

        // saved hotbars
        int gy = y;
        for (int r = 0; r < Hotbars.ROWS; r++) {
            int row = page * Hotbars.ROWS + r;
            ItemStack[] items = Hotbars.row(row);
            boolean sel = row == selected;
            if (sel) Gui.drawRect(gx - 2, gy + r * S - 1, gx + gw + 2, gy + r * S + S + 1, 0xFFF5A9B8);
            for (int k = 0; k < 9; k++) slot(mx, my, gx + k * S, gy + r * S, items, k, row);
        }
        // live hotbar
        int ly = gy + Hotbars.ROWS * S + 6;
        Gui.drawRect(gx - 2, ly - 3, gx + gw + 2, ly - 2, 0x60FFFFFF);
        for (int k = 0; k < 9; k++) {
            int sx = gx + k * S;
            Gui.drawRect(sx + 1, ly + 1, sx + S - 1, ly + S - 1, k == mc.player.inventory.currentItem ? 0xFF6A3FA0 : 0xFF241C33);
            item(mc.player.inventory.getStackInSlot(k), sx + 2, ly + 2);
        }
        if (in(mx, my, gx, ly, gw, S)) hint = "Your hotbar now. Scroll over a saved row (or right click it) to swap it in.";

        // left: builder switches
        int cx = gx - 28, cy = gy;
        for (String[] c : Caps.ALL) {
            boolean on = Caps.on(c[0]), over = in(mx, my, cx, cy, 22, 22);
            Gui.drawRect(cx, cy, cx + 22, cy + 22, on ? 0xFF2A6A3A : over ? 0xFF3A2E52 : 0xFF241C33);
            Gui.drawRect(cx, cy, cx + 22, cy + 1, on ? 0xFF8CE06A : 0x40FFFFFF);
            String ic = icon(c[0]);
            fontRenderer.drawStringWithShadow(ic, cx + 11 - fontRenderer.getStringWidth(ic) / 2f, cy + 7, on ? 0xFFFFFF : 0x9A92AE);
            if (over) hint = "§f" + c[1] + (on ? " §a(on)" : " §8(off)") + "§7 — " + c[2];
            hits.add(new Object[]{ cx, cy, 22, 22, (Runnable) () -> Caps.toggle(c[0]) });
            cy += 24;
        }

        // right: page, flight speed, settings, clear
        int rx = gx + gw + 8;
        fontRenderer.drawStringWithShadow("Page " + (page + 1), rx, gy, 0xFFFFFF);
        btn(mx, my, rx, gy + 11, 14, 14, "▲", 0xFF2A2238, () -> { page = Math.max(0, page - 1); }, "Previous page of saved hotbars");
        btn(mx, my, rx + 16, gy + 11, 14, 14, "▼", 0xFF2A2238, () -> { page = Math.min(Hotbars.pages() - 1, page + 1); }, "Next page (there's always an empty one)");
        int fl = ClientState.v("flight");
        fontRenderer.drawStringWithShadow(fl + "%", rx, gy + 31, 0x5BCEFA);
        sliderX = rx + 4; sliderY = gy + 42; sliderH = Hotbars.ROWS * S - 70;
        Gui.drawRect(sliderX, sliderY, sliderX + 10, sliderY + sliderH, 0xFF241C33);
        int fill = (int) ((long) Math.min(fl, 999) * sliderH / 999);
        PrideFrame.gradient(sliderX + 1, sliderY + sliderH - fill, sliderX + 9, sliderY + sliderH, 0xFFF5A9B8, 0xFF5BCEFA);
        if (in(mx, my, sliderX - 2, sliderY, 14, sliderH)) hint = "Flight speed (100% = normal). Drag, click or scroll. Shift-scroll = big steps.";
        int by = ly - 24;
        btn(mx, my, rx, by, 22, 18, "⚙", 0xFF8A2238, () -> { ClientState.S.tab = 8; mc.displayGuiScreen(new StudioScreen(null)); }, "Pride Studio settings");
        btn(mx, my, rx, ly, 22, 18, "✖", clearArmed ? 0xFFE40303 : 0xFF8A2238, () -> { if (clearArmed) { Hotbars.clear(); clearArmed = false; ClientState.say("Saved hotbars cleared."); } else clearArmed = true; },
                clearArmed ? "§cClick again to delete ALL saved hotbars" : "Clear all saved hotbars (asks twice)");

        // hint line under everything
        String line = !hint.isEmpty() ? hint : carried.isEmpty() ? "§8Click an item to move it • right click a row to swap it in • let go of Alt to close" : "§dMoving " + carried.getDisplayName() + " — click a slot to drop it";
        fontRenderer.drawStringWithShadow(line, (width - fontRenderer.getStringWidth(line)) / 2f, Math.max(2, py - 12), 0xFFFFFF);
        if (!carried.isEmpty()) item(carried, mx - 8, my - 8);
    }

    static String icon(String k) {
        switch (k) {
            case "bulldozer": return "⛏";
            case "reach": return "∞";
            case "angel": return "✦";
            case "replace": return "⇄";
            case "fastplace": return "»";
            case "tinker": return "⚒";
            case "bright": return "☀";
            default: return "✈";
        }
    }

    private void slot(int mx, int my, int x, int y, ItemStack[] items, int k, int row) {
        boolean over = in(mx, my, x, y, S, S);
        Gui.drawRect(x + 1, y + 1, x + S - 1, y + S - 1, over ? 0xFF3A2E52 : 0xFF1C1530);
        item(items[k], x + 2, y + 2);
        if (over && !items[k].isEmpty()) hint = items[k].getDisplayName();
        hits.add(new Object[]{ x, y, S, S, (Runnable) () -> {
            ItemStack here = items[k];
            items[k] = carried;
            carried = here;
            Hotbars.save();
        }, row });
    }

    private void item(ItemStack s, int x, int y) {
        if (s == null || s.isEmpty()) return;
        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        try { itemRender.renderItemAndEffectIntoGUI(s, x, y); itemRender.renderItemOverlays(fontRenderer, s, x, y); } catch (Throwable ignored) { }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
    }

    private void btn(int mx, int my, int x, int y, int w, int h, String label, int color, Runnable r, String tip) {
        if (PrideFrame.button(x, y, w, h, label, color, mx, my)) hint = tip;
        hits.add(new Object[]{ x, y, w, h, r });
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    @Override
    protected void mouseClicked(int mx, int my, int b) throws IOException {
        mx = (int) (mx / k); my = (int) (my / k);
        if (in(mx, my, sliderX - 2, sliderY, 14, sliderH) && b == 0) { dragging = true; setFlight(my); return; }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            if (!in(mx, my, (Integer) h[0], (Integer) h[1], (Integer) h[2], (Integer) h[3])) continue;
            if (b == 1 && h.length > 5) { Hotbars.swap((Integer) h[5]); selected = -1; click(); return; }   // right click a saved row = swap it in now
            if (b != 0) return;
            click();
            ((Runnable) h[4]).run();
            ClientState.save();
            return;
        }
    }

    private void setFlight(int my) {
        int v = (int) ((long) (sliderY + sliderH - my) * 999 / Math.max(1, sliderH));
        ClientState.S.vals.put("flight", Math.max(10, Math.min(999, v)));
    }

    @Override protected void mouseClickMove(int mx, int my, int b, long t) { if (dragging) setFlight((int) (my / k)); }
    @Override protected void mouseReleased(int mx, int my, int s) { if (dragging) { dragging = false; ClientState.save(); } }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mx = (int) ((Mouse.getEventX() * width / mc.displayWidth) / k), my = (int) ((height - Mouse.getEventY() * height / mc.displayHeight - 1) / k);
        int d = wheel > 0 ? -1 : 1;
        if (in(mx, my, sliderX - 6, sliderY - 14, 22, sliderH + 14)) {
            ClientState.S.vals.put("flight", Math.max(10, Math.min(999, ClientState.v("flight") - d * (isShiftKeyDown() ? 50 : 10))));
            ClientState.save();
            return;
        }
        // like Axiom: the wheel picks which saved row loads when you let go of Alt
        int first = page * Hotbars.ROWS;
        if (selected < 0) selected = first + Hotbars.ROWS - 1;
        selected += d;
        if (selected < first) selected = first + Hotbars.ROWS - 1;
        if (selected >= first + Hotbars.ROWS) selected = first;
    }

    private void click() { if (ClientState.S.sounds) mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.3F)); }

    @Override
    public void updateScreen() {
        if (ClientState.S.menuToggle || sticky) return;
        int key = StudioClient.MENU.getKeyCode();
        boolean held = key > 0 ? Keyboard.isKeyDown(key) : key < 0 && Mouse.isButtonDown(key + 100);
        if (!held && System.currentTimeMillis() - openedAt > 150) close();
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE || (ClientState.S.menuToggle && key == StudioClient.MENU.getKeyCode())) close();
    }

    private void close() {
        if (selected >= 0) Hotbars.swap(selected);
        if (!carried.isEmpty()) {                    // never lose a carried item: put it in the first empty saved slot
            outer:
            for (int r = 0; ; r++) { ItemStack[] row = Hotbars.row(r); for (int k = 0; k < 9; k++) if (row[k].isEmpty()) { row[k] = carried; break outer; } }
            carried = ItemStack.EMPTY;
            Hotbars.save();
        }
        mc.displayGuiScreen(null);
    }

    @Override public void onGuiClosed() { Hotbars.save(); ClientState.save(); }

    @Override public void drawDefaultBackground() { }
}
