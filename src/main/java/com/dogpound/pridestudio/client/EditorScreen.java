package com.dogpound.pridestudio.client;

import com.dogpound.pridestudio.PrideFrame;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pride Studio Editor Mode: the world with a free mouse, Axiom-style windows in the Pride look.
 * <pre>
 *  ┌ File  Edit  Select  View  Operations  Masks  Help ───────────────────────────┐
 *  │ Tools      │                                                │ Tool Options    │
 *  │ (scroll)   │            the world, brush ring               │ sliders, cycles │
 *  │            │                                                │ toggles, block  │
 *  └ status: key hints · selection size · fly speed ──────────────────────────────┘
 * </pre>
 * Left-drag uses the tool (Ctrl = opposite), right/middle-drag orbits (Shift = pan), wheel zooms (Ctrl = size),
 * WASD/Space/C fly, F focuses, Alt-click picks a block, Esc or ` leaves. Every WorldEdit and VoxelSniper tool
 * of the Pride Studio panel is here too (VoxelSniper category + Operations menu).
 */
public class EditorScreen extends GuiScreen {
    // ---------------------------------------------------------------- tool + option catalogue

    static final class Opt {
        final String key, label, kind; final int min, max, def; final String[] values;
        Opt(String key, String label, String kind, int min, int max, int def, String... values) { this.key = key; this.label = label; this.kind = kind; this.min = min; this.max = max; this.def = def; this.values = values; }
    }

    static final Map<String, Opt> OPTS = new LinkedHashMap<>();
    static void opt(String k, String l, String kind, int min, int max, int def, String... v) { OPTS.put(k, new Opt(k, l, kind, min, max, def, v)); }
    static {
        opt("size", "Size", "int", 0, 32, 5);
        opt("strength", "Strength", "int", 1, 8, 2);
        opt("shape", "Brush shape", "cycle", 0, 0, 0, "sphere", "cube", "octahedron", "cylinder", "disc");
        opt("block", "Block", "block", 0, 0, 0);
        opt("surface", "Surface only", "bool", 0, 1, 0);
        opt("keep", "Keep existing", "bool", 0, 1, 0);
        opt("useMask", "Use mask", "bool", 0, 1, 0);
        opt("scale", "Noise scale", "int", 1, 64, 8);
        opt("octaves", "Octaves", "int", 1, 6, 2);
        opt("noiseCount", "Blocks (recent)", "int", 2, 8, 3);
        opt("gmode", "Direction", "cycle", 0, 0, 0, "vertical", "radial");
        opt("biome", "Biome", "cycle", 0, 0, 0, "plains", "forest", "desert", "taiga", "swampland", "jungle", "savanna", "mesa", "ice_plains", "mushroom_island", "birch_forest", "roofed_forest", "extreme_hills", "beaches", "ocean", "river", "hell", "sky");
        opt("emode", "Mode", "cycle", 0, 0, 0, "raise", "lower", "flatten", "smooth");
        opt("falloff", "Falloff", "cycle", 0, 0, 0, "smooth", "linear", "sharp", "flat");
        opt("ratio", "Mass / chance %", "int", 10, 200, 100);
        opt("width", "Crack width", "int", 1, 6, 1);
        opt("useActive", "Fill with block", "bool", 0, 1, 0);
        opt("count", "Layers", "int", 1, 16, 1);
        opt("compare", "Match", "cycle", 0, 0, 0, "same", "solid");
        opt("limit", "Limit", "int", 100, 200000, 20000);
        opt("up", "Flow upward too", "bool", 0, 1, 0);
        opt("exact", "Exact state", "bool", 0, 1, 0);
        opt("corners", "Through corners", "bool", 0, 1, 0);
        opt("tree", "Tree", "cycle", 0, 0, 0, "oak", "birch", "tallbirch", "spruce", "pine", "big", "jungle", "megajungle", "acacia", "darkoak", "swamp", "mixed");
        opt("density", "Density %", "int", 1, 100, 25);
        opt("height", "Height / depth", "int", 1, 32, 4);
        opt("seed", "Seed (0 = random)", "int", 0, 999, 0);
    }

    static final class Tool {
        final String id, name, cat, desc; final int color; final String[] opts;
        Tool(String cat, String id, String name, int color, String desc, String... opts) { this.cat = cat; this.id = id; this.name = name; this.color = 0xFF000000 | color; this.desc = desc; this.opts = opts; }
    }

    static final List<Tool> TOOLS = new ArrayList<>();
    static void t(String cat, String id, String name, int color, String desc, String... o) { TOOLS.add(new Tool(cat, id, name, color, desc, o)); }
    static final String[] BASE = {"size", "shape", "block", "useMask"};
    static {
        t("Select", "boxselect", "Box select", 0xF5A9B8, "Click two corners (left = corner 1, Ctrl+left = corner 2)");
        t("Select", "magicselect", "Magic select", 0xF5A9B8, "Click a block: selects everything connected that's the same", "exact", "corners", "limit");
        t("Terrain", "elevation", "Elevation", 0x5CFF7A, "World-Painter style: raise / lower / flatten / smooth columns with a soft falloff", "size", "strength", "emode", "falloff");
        t("Terrain", "raise", "Raise", 0x5CFF7A, "Pull the ground up (Ctrl: down)", "size", "strength");
        t("Terrain", "lower", "Lower", 0xE8434F, "Push the ground down (Ctrl: up)", "size", "strength");
        t("Terrain", "flatten", "Flatten", 0xF2C94C, "Level to the clicked height (Ctrl: only cut)", "size", "height", "block");
        t("Terrain", "gsmooth", "Smooth", 0x5BCEFA, "Gaussian smooth that keeps the amount of land (mass %)", "size", "strength", "shape", "ratio", "useMask");
        t("Terrain", "blend", "Blend", 0x5BCEFA, "VoxelSniper blend: bumps and holes melt into their neighbours (Ctrl: only shave)", "size", "strength");
        t("Terrain", "weld", "Weld", 0xB48CFF, "Gaussian: only adds land, fills seams and gaps", "size", "strength", "shape", "useActive", "block");
        t("Terrain", "melt", "Melt", 0xC9A27A, "Gaussian: only removes land, softens sharp bits", "size", "strength", "shape");
        t("Terrain", "erode", "Erode", 0xC9A27A, "Weather away exposed edges (Ctrl: fill nooks)", "size", "strength");
        t("Terrain", "fillin", "Fill in", 0x8FB4FF, "Fill gaps and nooks (Ctrl: erode)", "size", "strength");
        t("Paint", "painter", "Painter", 0xFF7AD8, "Repaint solid blocks with your block (mixes like 50%stone,50%dirt work)", "size", "shape", "block", "surface", "useMask");
        t("Paint", "noisepaint", "Noise paint", 0xFF7AD8, "Paint a natural noise mix of your last few blocks", "size", "shape", "noiseCount", "scale", "octaves", "seed", "surface", "useMask");
        t("Paint", "gradient", "Gradient", 0xFF7AD8, "Fade from your block (top) to your previous block (bottom)", "size", "shape", "gmode", "block", "surface", "useMask");
        t("Paint", "overlay", "Paint top", 0xFF7AD8, "Repaint the top layers of the ground (Ctrl: add a layer)", "size", "height", "block");
        t("Paint", "biomebrush", "Biome", 0x4AD06A, "Paint a biome onto the land (grass and water colours change)", "size", "biome");
        t("Draw", "freehand", "Draw", 0xB48CFF, "Add your block in the brush shape (Ctrl: remove)", "size", "shape", "block", "keep", "useMask");
        t("Draw", "rock", "Rock", 0xA0A0B0, "Lumpy noise boulders of your block (Ctrl: carve)", "size", "strength", "block", "keep", "seed");
        t("Draw", "tree", "Trees", 0x4AD06A, "Plant a tree where you click", "tree");
        t("Draw", "snow", "Snow", 0xF2F2FF, "Snow layers (Ctrl: melt)", "size");
        t("Shape", "distort", "Distort", 0xFFB347, "Warp blocks with noise", "size", "strength", "shape", "scale", "seed");
        t("Shape", "roughen", "Roughen", 0xFFB347, "Exposed blocks wear away (strength = open faces needed)", "size", "strength", "ratio", "shape");
        t("Shape", "shatter", "Shatter", 0xFFB347, "Voronoi cracks through rock", "size", "shape", "scale", "width", "useActive", "block", "seed");
        t("Shape", "extrude", "Extrude", 0xFFB347, "Grow the clicked face outward (Ctrl: shrink it)", "size", "count", "compare");
        t("Fluid", "floodfill", "Flood fill", 0x3A7AE8, "Fill connected air with your block (water by default)", "limit", "up", "block");
        t("Fluid", "pour", "Pour", 0x3A7AE8, "Pour into the low part of the brush (lakes, ponds)", "size", "block");
        t("Fluid", "drain", "Drain", 0x3A7AE8, "Remove water and lava", "size");
        t("Utility", "pick", "Pick block", 0xFFFFFF, "Click a block to make it your block (or Alt + click any time)");
        t("Utility", "ruler", "Ruler", 0xFFFFFF, "Click two points: distance, size, slope");
        for (String b : com.dogpound.pridestudio.edit.BrushOps.TYPES)
            if (!b.equals("tree") && !b.equals("snow") && !b.equals("drain"))
                t("VoxelSniper", b, "VS " + b, 0x8A8499, "VoxelSniper's " + b + " brush", "size", "height", "density", "strength", "block", "useMask");
    }

    // ---------------------------------------------------------------- state

    private Tool tool;
    private int stroke, lastRawX, lastRawY, toolScroll;
    private boolean painting, cornerToggle;
    private long lastDab;
    private BlockPos lastDabAt, ruler1, ruler2;
    private String tip, openMenu, dragOpt;
    private static boolean showTools = true, showOptions = true, showHelp;

    public EditorScreen() {
        String saved = opt("tool", "elevation");
        for (Tool t : TOOLS) if (t.id.equals(saved)) tool = t;
        if (tool == null) tool = TOOLS.get(2);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void initGui() { Keyboard.enableRepeatEvents(true); mc.gameSettings.hideGUI = true; }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    static String opt(String k, String def) { String v = ClientState.S.opts.get(k); return v == null ? def : v; }
    static int ival(String k) { Opt o = OPTS.get(k); return ClientState.S.val(k, o == null ? 1 : o.def); }
    static boolean bval(String k) {
        if (k.equals("useMask")) return ClientState.S.useMask;
        return ClientState.S.val(k, OPTS.get(k).def) != 0;
    }
    static String cval(String k) { Opt o = OPTS.get(k); return opt(k, o.values[0]); }

    static int radius() { return Math.max(0, Math.min(32, ival("size"))); }

    static float[] toolColor() {
        int c = 0xF5A9B8;
        if (Minecraft().currentScreen instanceof EditorScreen) c = ((EditorScreen) Minecraft().currentScreen).tool.color;
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) c = 0xE8434F;
        return new float[]{(c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f};
    }

    private static net.minecraft.client.Minecraft Minecraft() { return net.minecraft.client.Minecraft.getMinecraft(); }

    // ---------------------------------------------------------------- layout

    private static final int MENU_H = 11, STATUS_H = 12, TOOLS_W = 92, OPTS_W = 132;
    private int toolsX() { return 2; }
    private int optsX() { return width - OPTS_W - 2; }
    private int top() { return MENU_H + 2; }
    private int bottom() { return height - STATUS_H - 2; }

    private boolean overUi(int mx, int my) {
        if (my < MENU_H || my >= height - STATUS_H) return true;
        if (showTools && mx < toolsX() + TOOLS_W + 2) return true;
        if (showOptions && mx >= optsX() - 2) return true;
        return openMenu != null && menuRect(mx, my);
    }

    // ---------------------------------------------------------------- frame

    private int rawX() { return Mouse.getX(); }
    private int rawY() { return mc.displayHeight - Mouse.getY() - 1; }

    @Override
    public void updateScreen() {
        if (!EditorMode.active()) { mc.displayGuiScreen(null); return; }
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) return;
        double f = (Keyboard.isKeyDown(Keyboard.KEY_W) ? 1 : 0) - (Keyboard.isKeyDown(Keyboard.KEY_S) ? 1 : 0);
        double s = (Keyboard.isKeyDown(Keyboard.KEY_D) ? 1 : 0) - (Keyboard.isKeyDown(Keyboard.KEY_A) ? 1 : 0);
        double v = (Keyboard.isKeyDown(Keyboard.KEY_SPACE) ? 1 : 0) - (Keyboard.isKeyDown(Keyboard.KEY_C) ? 1 : 0);
        double speed = ClientState.v("flight") / 100.0 * (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Mouse.isButtonDown(1) ? 2.6 : 1);
        if (f != 0 || s != 0 || v != 0) EditorMode.fly(f, s, v, 0.6 * speed);
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        int rx = rawX(), ry = rawY();
        if (Mouse.isButtonDown(1) || Mouse.isButtonDown(2)) {
            int dx = rx - lastRawX, dy = ry - lastRawY;
            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) EditorMode.pan(dx, dy);
            else EditorMode.orbit(dx * 0.35F, dy * 0.35F);
        }
        lastRawX = rx;
        lastRawY = ry;
        EditorMode.hover = overUi(mx, my) ? null : EditorMode.pick(rx, ry);
        EditorMode.ruler1 = ruler1;
        EditorMode.ruler2 = ruler2;
        if (painting && Mouse.isButtonDown(0)) dab(false); else painting = false;
        if (dragOpt != null && Mouse.isButtonDown(0)) dragSlider(mx); else dragOpt = null;

        if (showTools) drawTools(mx, my);
        if (showOptions) drawOptions(mx, my);
        drawStatus(mx, my);
        drawMenuBar(mx, my);
        if (showHelp) drawHelp();
        if (tip != null && openMenu == null) drawHoveringText(tip, mx, my);
        tip = null;
        super.drawScreen(mx, my, pt);
    }

    // ---------------------------------------------------------------- tools window

    private void drawTools(int mx, int my) {
        int x = toolsX(), y = top(), h = bottom() - top();
        PrideFrame.gradient(x, y, x + TOOLS_W, y + h, 0xE0140E22, 0xE8080510);
        Gui.drawRect(x, y, x + TOOLS_W, y + 1, PrideFrame.PINK);
        fontRenderer.drawStringWithShadow("§lTools", x + 4, y + 3, 0xFFFFFF);
        PrideFrame.clip(x, y + 13, TOOLS_W, h - 14);
        int ry = y + 14 - toolScroll;
        String cat = "";
        for (Tool t : TOOLS) {
            if (!t.cat.equals(cat)) {
                cat = t.cat;
                fontRenderer.drawStringWithShadow("§8" + cat.toUpperCase(), x + 4, ry + 2, 0xFFFFFF);
                ry += 11;
            }
            boolean on = t == tool, over = in(mx, my, x + 2, ry, TOOLS_W - 6, 11) && my > y + 13 && my < y + h;
            if (on || over) Gui.drawRect(x + 2, ry, x + TOOLS_W - 4, ry + 11, on ? PrideFrame.TILE_ON : 0xFF2A2140);
            Gui.drawRect(x + 4, ry + 3, x + 8, ry + 7, t.color);
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(t.name, TOOLS_W - 18), x + 11, ry + 2, on ? 0xFFFFFF : 0xCFC6E0);
            if (over) tip = t.name + ": " + t.desc;
            ry += 11;
        }
        PrideFrame.unclip();
        int total = ry + toolScroll - (y + 14);
        PrideFrame.scrollbar(x + TOOLS_W - 4, y + 14, h - 15, toolScroll, h - 15, total);
    }

    private Tool toolAt(int mx, int my) {
        int x = toolsX(), y = top(), h = bottom() - top();
        if (!in(mx, my, x, y + 13, TOOLS_W, h - 14)) return null;
        int ry = y + 14 - toolScroll;
        String cat = "";
        for (Tool t : TOOLS) {
            if (!t.cat.equals(cat)) { cat = t.cat; ry += 11; }
            if (my >= ry && my < ry + 11) return t;
            ry += 11;
        }
        return null;
    }

    // ---------------------------------------------------------------- options window

    private void drawOptions(int mx, int my) {
        int x = optsX(), y = top(), h = bottom() - top();
        PrideFrame.gradient(x, y, x + OPTS_W, y + h, 0xE0140E22, 0xE8080510);
        Gui.drawRect(x, y, x + OPTS_W, y + 1, tool.color);
        fontRenderer.drawStringWithShadow("§l" + tool.name, x + 4, y + 3, 0xFFFFFF);
        List<String> desc = fontRenderer.listFormattedStringToWidth("§7" + tool.desc, OPTS_W - 8);
        int ry = y + 14;
        for (String d : desc) { fontRenderer.drawStringWithShadow(d, x + 4, ry, 0xFFFFFF); ry += 9; }
        ry += 3;
        for (String k : opts()) {
            Opt o = OPTS.get(k);
            if (o == null) continue;
            ry = drawOpt(o, x + 4, ry, OPTS_W - 8, mx, my);
        }
        if (tool.id.equals("ruler")) ry = drawRuler(x + 4, ry + 2);
        // selection + history at the bottom of the window
        int by = bottom() - 34;
        Gui.drawRect(x + 4, by - 3, x + OPTS_W - 4, by - 2, 0x40FFFFFF);
        String sel = ClientState.pos1 != null && ClientState.pos2 != null ? size(ClientState.pos1, ClientState.pos2) : "§8none";
        fontRenderer.drawStringWithShadow("§7Selection: §f" + sel, x + 4, by, 0xFFFFFF);
        fontRenderer.drawStringWithShadow("§7Undo steps: §f" + ClientState.history.size() + (ClientState.redo > 0 ? " §7(redo " + ClientState.redo + ")" : ""), x + 4, by + 10, 0xFFFFFF);
        if (!ClientState.msg.isEmpty() && System.currentTimeMillis() - ClientState.msgAt < 5000)
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(ClientState.msg, OPTS_W - 8), x + 4, by + 20, 0xFFFFFF);
    }

    private String[] opts() { return tool.opts.length == 0 ? new String[0] : tool.opts; }

    private int drawOpt(Opt o, int x, int y, int w, int mx, int my) {
        boolean over = in(mx, my, x, y, w, o.kind.equals("int") ? 20 : 12);
        switch (o.kind) {
            case "int": {
                int v = ival(o.key);
                fontRenderer.drawStringWithShadow("§7" + o.label, x, y, 0xFFFFFF);
                String s = String.valueOf(v);
                fontRenderer.drawStringWithShadow(s, x + w - fontRenderer.getStringWidth(s), y, 0xF5A9B8);
                int by = y + 11;
                Gui.drawRect(x, by, x + w, by + 5, 0xFF1C1530);
                int fill = (int) ((v - o.min) / (double) (o.max - o.min) * w);
                Gui.drawRect(x, by, x + fill, by + 5, tool.color);
                Gui.drawRect(x + fill - 1, by - 1, x + fill + 1, by + 6, 0xFFFFFFFF);
                if (over) tip = o.label + ": drag, or scroll over it" + (o.key.equals("size") ? " (Ctrl + wheel / [ ])" : "");
                return y + 20;
            }
            case "cycle": {
                String v = cval(o.key);
                PrideFrame.button(x, y, w, 11, fontRenderer.trimStringToWidth(o.label + ": §d" + v, w - 6), PrideFrame.BUTTON, mx, my);
                if (over) tip = o.label + ": click for the next, right-click for the previous";
                return y + 13;
            }
            case "bool": {
                boolean v = bval(o.key);
                Gui.drawRect(x, y + 1, x + 9, y + 10, v ? tool.color : 0xFF1C1530);
                if (v) fontRenderer.drawStringWithShadow("✔", x + 1, y + 2, 0xFFFFFF);
                fontRenderer.drawStringWithShadow(o.label, x + 13, y + 2, v ? 0xFFFFFF : 0xA89FBF);
                return y + 13;
            }
            case "block": {
                PrideFrame.button(x, y, w, 12, fontRenderer.trimStringToWidth("■ " + ClientState.S.block, w - 6), 0xFF3D2168, mx, my);
                if (over) tip = "Your block: click to open the block picker, or Alt + click a block in the world";
                return y + 14;
            }
            default: return y;
        }
    }

    private int drawRuler(int x, int y) {
        if (ruler1 == null) { fontRenderer.drawStringWithShadow("§7Click the first point", x, y, 0xFFFFFF); return y + 10; }
        if (ruler2 == null) { fontRenderer.drawStringWithShadow("§7Click the second point", x, y, 0xFFFFFF); return y + 10; }
        int dx = Math.abs(ruler2.getX() - ruler1.getX()), dy = Math.abs(ruler2.getY() - ruler1.getY()), dz = Math.abs(ruler2.getZ() - ruler1.getZ());
        double eu = Math.sqrt(dx * dx + dy * dy + dz * dz), flat = Math.sqrt(dx * dx + dz * dz);
        String[] lines = {String.format("§7Distance: §f%.1f", eu), "§7Blocks walked: §f" + (dx + dy + dz), "§7Size: §f" + (dx + 1) + " × " + (dy + 1) + " × " + (dz + 1),
                String.format("§7Slope: §f%.1f°", Math.toDegrees(Math.atan2(dy, Math.max(0.0001, flat))))};
        for (String l : lines) { fontRenderer.drawStringWithShadow(l, x, y, 0xFFFFFF); y += 10; }
        return y;
    }

    private Opt optAt(int mx, int my) {
        if (!showOptions) return null;
        int x = optsX() + 4, w = OPTS_W - 8;
        int ry = top() + 14 + fontRenderer.listFormattedStringToWidth("§7" + tool.desc, OPTS_W - 8).size() * 9 + 3;
        for (String k : opts()) {
            Opt o = OPTS.get(k);
            if (o == null) continue;
            int h = o.kind.equals("int") ? 20 : o.kind.equals("block") ? 14 : 13;
            if (in(mx, my, x, ry, w, h)) return o;
            ry += h;
        }
        return null;
    }

    private void dragSlider(int mx) {
        Opt o = OPTS.get(dragOpt);
        if (o == null) return;
        int x = optsX() + 4, w = OPTS_W - 8;
        double f = Math.max(0, Math.min(1, (mx - x) / (double) w));
        setInt(o, (int) Math.round(o.min + f * (o.max - o.min)));
    }

    private void setInt(Opt o, int v) {
        ClientState.S.vals.put(o.key, Math.max(o.min, Math.min(o.max, v)));
        ClientState.save();
    }

    private void clickOpt(Opt o, int mx, int button) {
        switch (o.kind) {
            case "int": dragOpt = o.key; dragSlider(mx); break;
            case "cycle": {
                String cur = cval(o.key);
                int i = 0;
                for (int k = 0; k < o.values.length; k++) if (o.values[k].equals(cur)) i = k;
                i = (i + (button == 1 ? o.values.length - 1 : 1)) % o.values.length;
                ClientState.S.opts.put(o.key, o.values[i]);
                ClientState.save();
                break;
            }
            case "bool":
                if (o.key.equals("useMask")) ClientState.S.useMask = !ClientState.S.useMask;
                else ClientState.S.vals.put(o.key, bval(o.key) ? 0 : 1);
                ClientState.save();
                break;
            case "block": mc.displayGuiScreen(new StudioScreen(this)); break;
            default:
        }
    }

    // ---------------------------------------------------------------- status bar + menus

    private void drawStatus(int mx, int my) {
        int y = height - STATUS_H;
        Gui.drawRect(0, y, width, height, 0xF0100A1C);
        int sw = width / PrideFrame.RAINBOW.length;
        for (int i = 0; i < PrideFrame.RAINBOW.length; i++) Gui.drawRect(i * sw, y, i == PrideFrame.RAINBOW.length - 1 ? width : (i + 1) * sw, y + 1, PrideFrame.RAINBOW[i]);
        RayTraceResult h = EditorMode.hover;
        String where = h != null && h.typeOfHit == RayTraceResult.Type.BLOCK
                ? mc.world.getBlockState(h.getBlockPos()).getBlock().getLocalizedName() + " §7" + h.getBlockPos().getX() + " " + h.getBlockPos().getY() + " " + h.getBlockPos().getZ()
                : "§8—";
        fontRenderer.drawStringWithShadow("§d" + tool.name + "§7 · L-drag use · Ctrl opposite · R-drag orbit · F1 keys · §f" + where, 4, y + 3, 0xFFFFFF);
        String fs = "Fly " + ClientState.v("flight") + "%";
        fontRenderer.drawStringWithShadow("§7" + fs, width - fontRenderer.getStringWidth(fs) - 4, y + 3, 0xFFFFFF);
    }

    static final String[] MENUS = {"File", "Edit", "Select", "View", "Operations", "Masks", "Help"};

    /** label, action id (or "-" for a divider) */
    private String[][] items(String menu) {
        switch (menu) {
            case "File": return new String[][]{{"Full Studio panel…", "panel"}, {"Builder menu…", "menu"}, {"-", ""}, {"Exit Editor Mode   Esc", "exit"}};
            case "Edit": return new String[][]{{"Undo   Ctrl+Z", "undo"}, {"Redo   Ctrl+Y", "redo"}, {"-", ""}, {"Copy selection   Ctrl+C", "copy"}, {"Cut selection", "cut"},
                    {"Paste at the mouse   Ctrl+V", "paste"}, {"Rotate clipboard 90°", "rotate"}, {"Flip clipboard", "flip"}, {"-", ""}, {"Clear history", "clearhist"}};
            case "Select": return new String[][]{{"Box select tool", "tool:boxselect"}, {"Magic select tool", "tool:magicselect"}, {"Select chunk at mouse", "selchunk"},
                    {"Clear selection", "selclear"}, {"-", ""}, {"Grow 1 every way", "expand"}, {"Shrink 1 every way", "contract"}, {"Bedrock to sky", "fullheight"}};
            case "View": return new String[][]{{(showTools ? "✔ " : "   ") + "Tools window", "v:tools"}, {(showOptions ? "✔ " : "   ") + "Tool Options window", "v:options"},
                    {(ClientState.S.showBox ? "✔ " : "   ") + "Selection box", "v:box"}, {"-", ""}, {"Look straight down", "v:top"}, {"Look from the side", "v:side"},
                    {"Focus the mouse spot   F", "v:focus"}, {"Back to my body", "v:home"}, {"-", ""}, {"Fly speed +25%", "v:fast"}, {"Fly speed -25%", "v:slow"}};
            case "Operations": return new String[][]{{"Fill selection with block", "set"}, {"Replace mask → block", "replace"}, {"Walls", "walls"}, {"Outline", "outline"},
                    {"Hollow", "hollow"}, {"Overlay top", "overlay"}, {"Naturalize", "naturalize"}, {"Smooth selection", "smooth"}, {"Drain water/lava", "drainsel"},
                    {"Set biome (Biome tool's)", "setbiome"}, {"Clear to air", "clear"}, {"Count blocks", "count"}, {"-", ""}, {"Stack ×1 (look direction)", "stack"}, {"Move 1 up", "moveup"}};
            case "Masks": return new String[][]{{(ClientState.S.useMask ? "✔ " : "   ") + "Use the mask", "m:use"}, {"Mask = my block", "m:set"}, {"Mask = block at mouse", "m:look"},
                    {"Clear mask", "m:clear"}, {"-", ""}, {(bval("surface") ? "✔ " : "   ") + "Surface only", "m:surface"}, {(bval("keep") ? "✔ " : "   ") + "Keep existing", "m:keep"}};
            default: return new String[][]{{(showHelp ? "✔ " : "   ") + "Show all keys", "h:keys"}, {"What is this?", "h:about"}};
        }
    }

    private int menuX(String menu) {
        int x = 4;
        for (String m : MENUS) { if (m.equals(menu)) return x; x += fontRenderer.getStringWidth(m) + 10; }
        return x;
    }

    private boolean menuRect(int mx, int my) {
        int x = menuX(openMenu), w = 150, h = items(openMenu).length * 11 + 4;
        return in(mx, my, x - 2, MENU_H, w, h);
    }

    private void drawMenuBar(int mx, int my) {
        Gui.drawRect(0, 0, width, MENU_H, 0xF0100A1C);
        Gui.drawRect(0, MENU_H - 1, width, MENU_H, 0x60F5A9B8);
        int x = 4;
        for (String m : MENUS) {
            int w = fontRenderer.getStringWidth(m);
            boolean over = in(mx, my, x - 3, 0, w + 6, MENU_H);
            if (over || m.equals(openMenu)) Gui.drawRect(x - 3, 0, x + w + 3, MENU_H - 1, PrideFrame.TILE_ON);
            fontRenderer.drawStringWithShadow(m, x, 2, 0xFFFFFF);
            if (over && openMenu != null && !m.equals(openMenu)) openMenu = m;     // slide between menus
            x += w + 10;
        }
        String title = "✦ Pride Studio · Editor";
        fontRenderer.drawStringWithShadow("§d" + title, width - fontRenderer.getStringWidth(title) - 4, 2, 0xFFFFFF);
        if (openMenu == null) return;
        String[][] it = items(openMenu);
        int bx = menuX(openMenu) - 2, by = MENU_H, w = 150;
        Gui.drawRect(bx, by, bx + w, by + it.length * 11 + 4, 0xF8160F26);
        Gui.drawRect(bx, by, bx + w, by + 1, PrideFrame.PINK);
        int y = by + 2;
        for (String[] i : it) {
            if (i[0].equals("-")) { Gui.drawRect(bx + 4, y + 5, bx + w - 4, y + 6, 0x40FFFFFF); y += 11; continue; }
            boolean over = in(mx, my, bx, y, w, 11);
            if (over) Gui.drawRect(bx + 1, y, bx + w - 1, y + 11, PrideFrame.TILE_ON);
            fontRenderer.drawStringWithShadow(i[0], bx + 6, y + 2, 0xFFFFFF);
            y += 11;
        }
    }

    private boolean clickMenu(int mx, int my) {
        if (my < MENU_H) {
            int x = 4;
            for (String m : MENUS) {
                int w = fontRenderer.getStringWidth(m);
                if (in(mx, my, x - 3, 0, w + 6, MENU_H)) { openMenu = m.equals(openMenu) ? null : m; return true; }
                x += w + 10;
            }
            openMenu = null;
            return true;
        }
        if (openMenu == null) return false;
        if (!menuRect(mx, my)) { openMenu = null; return true; }
        String[][] it = items(openMenu);
        int y = MENU_H + 2;
        for (String[] i : it) {
            if (my >= y && my < y + 11 && !i[0].equals("-")) { String a = i[1]; openMenu = null; action(a); return true; }
            y += 11;
        }
        return true;
    }

    private void action(String a) {
        NBTTagCompound t = ClientState.args();
        RayTraceResult h = EditorMode.hover;
        switch (a) {
            case "panel": mc.displayGuiScreen(new StudioScreen(this)); return;
            case "menu": mc.displayGuiScreen(new BuilderMenu()); return;
            case "exit": EditorMode.exit(); return;
            case "paste": if (h != null && h.typeOfHit == RayTraceResult.Type.BLOCK) ClientState.at(t, h.getBlockPos().up()); ClientState.send("paste", t); return;
            case "selchunk": if (h != null && h.typeOfHit == RayTraceResult.Type.BLOCK) ClientState.at(t, h.getBlockPos()); ClientState.send("selchunk", t); return;
            case "expand": case "contract": t.setString("dir", "all"); t.setInteger("amount", 1); ClientState.send(a, t); return;
            case "moveup": t.setString("dir", "up"); t.setInteger("amount", 1); ClientState.send("move", t); return;
            case "stack": t.setInteger("amount", 1); ClientState.send("stack", t); return;
            case "setbiome": t.setString("biome", cval("biome")); ClientState.send("setbiome", t); return;
            case "v:tools": showTools = !showTools; return;
            case "v:options": showOptions = !showOptions; return;
            case "v:box": ClientState.S.showBox = !ClientState.S.showBox; ClientState.save(); return;
            case "v:top": EditorMode.pitch = 89.9F; EditorMode.place(); return;
            case "v:side": EditorMode.pitch = 15F; EditorMode.place(); return;
            case "v:focus": EditorMode.focusHover(); return;
            case "v:home": EditorMode.home(); return;
            case "v:fast": ClientState.S.vals.put("flight", Math.min(800, ClientState.v("flight") + 25)); ClientState.save(); return;
            case "v:slow": ClientState.S.vals.put("flight", Math.max(25, ClientState.v("flight") - 25)); ClientState.save(); return;
            case "m:use": ClientState.S.useMask = !ClientState.S.useMask; ClientState.save(); return;
            case "m:set": ClientState.S.mask = ClientState.S.block; ClientState.S.useMask = true; ClientState.save(); ClientState.say("Mask: " + ClientState.S.mask); return;
            case "m:look": if (h != null && h.typeOfHit == RayTraceResult.Type.BLOCK) { ClientState.S.mask = id(mc.world.getBlockState(h.getBlockPos())); ClientState.S.useMask = true; ClientState.save(); ClientState.say("Mask: " + ClientState.S.mask); } return;
            case "m:clear": ClientState.S.mask = ""; ClientState.S.useMask = false; ClientState.save(); return;
            case "m:surface": ClientState.S.vals.put("surface", bval("surface") ? 0 : 1); ClientState.save(); return;
            case "m:keep": ClientState.S.vals.put("keep", bval("keep") ? 0 : 1); ClientState.save(); return;
            case "h:keys": showHelp = !showHelp; return;
            case "h:about": ClientState.say("Pride Studio Editor Mode: Axiom-style editing with WorldEdit + VoxelSniper inside. Open with `"); return;
            default:
                if (a.startsWith("tool:")) { for (Tool x : TOOLS) if (x.id.equals(a.substring(5))) setTool(x); return; }
                ClientState.send(a, t);                                               // undo, redo, copy, cut, rotate, flip, clearhist, fill ops, count…
        }
    }

    private void drawHelp() {
        String[] k = {"§lEditor keys", "Left-drag: use the tool   Ctrl: the opposite", "Right/middle-drag: orbit   + Shift: pan", "Wheel: zoom   Ctrl+wheel: brush size",
                "W A S D: fly   Space / C: up / down   Shift: faster", "F: focus on the mouse spot   [ ]: brush size", "Alt + click: pick that block   B: block picker",
                "Ctrl+Z / Ctrl+Y: undo / redo (one stroke = one undo)", "Ctrl+C / Ctrl+V: copy selection / paste at mouse", "Tab: hide windows   Esc or `: leave"};
        int w = 230, h = k.length * 10 + 8, x = (width - w) / 2, y = (height - h) / 2;
        PrideFrame.card(x, y, w, h, PrideFrame.PINK);
        Gui.drawRect(x, y, x + w, y + h, 0xC0000000);
        for (int i = 0; i < k.length; i++) fontRenderer.drawStringWithShadow(k[i], x + 6, y + 5 + i * 10, 0xFFFFFF);
    }

    // ---------------------------------------------------------------- using tools

    private void dab(boolean first) {
        RayTraceResult h = EditorMode.hover;
        if (h == null || h.typeOfHit != RayTraceResult.Type.BLOCK) return;
        BlockPos at = h.getBlockPos();
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL);
        String id = tool.id;
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || id.equals("pick")) { if (first) pick(at); return; }
        if (id.equals("ruler")) { if (first) { if (ruler1 == null || ruler2 != null) { ruler1 = at; ruler2 = null; } else ruler2 = at; } return; }
        if (id.equals("boxselect")) {
            if (!first) return;
            NBTTagCompound t = new NBTTagCompound();
            ClientState.at(t, at);
            boolean second = ctrl || cornerToggle;
            cornerToggle = !second;
            ClientState.send(second ? "pos2" : "pos1", t);
            return;
        }
        if (id.equals("magicselect")) {
            if (!first) return;
            NBTTagCompound t = ClientState.args();
            ClientState.at(t, at);
            for (String k : new String[]{"exact", "corners"}) t.setBoolean(k, bval(k));
            t.setInteger("limit", ival("limit"));
            ClientState.send("magicselect", t);
            return;
        }
        int r = radius();
        long now = System.currentTimeMillis();
        boolean single = id.equals("tree") || id.equals("floodfill") || id.equals("extrude") || id.equals("biomebrush") && !first;
        if (!first) {
            if (single) return;
            boolean moved = lastDabAt == null || lastDabAt.distanceSq(at) >= Math.max(1, r * r / 4.0);
            long gap = id.equals("raise") || id.equals("lower") || id.equals("elevation") ? 240 : 380;
            if ((!moved && now - lastDab < gap) || now - lastDab < 90) return;
        }
        lastDab = now;
        lastDabAt = at;
        NBTTagCompound t = ClientState.args();
        ClientState.at(t, at);
        t.setString("brush", id);
        t.setInteger("face", h.sideHit.getIndex());
        t.setBoolean("alt", ctrl);
        for (String k : tool.opts) {
            Opt o = OPTS.get(k);
            if (o == null) continue;
            switch (o.kind) {
                case "int": t.setInteger(k, ival(k)); break;
                case "bool": if (!k.equals("useMask")) t.setBoolean(k, bval(k)); break;
                case "cycle": t.setString(k.equals("gmode") || k.equals("emode") ? "mode" : k, cval(k)); break;
                default:
            }
        }
        t.setLong("seed", ival("seed"));
        if (id.equals("noisepaint")) t.setString("blocks", String.join(",", recent(ival("noiseCount"))));
        if (id.equals("gradient")) { List<String> rc = recent(2); t.setString("block2", rc.size() > 1 ? rc.get(1) : "stone"); }
        if (id.equals("overlay")) t.setInteger("height", Math.max(1, ival("height")));
        if (id.equals("flatten")) t.setInteger("height", Math.max(r + 2, ival("height")));
        if ((id.equals("floodfill") || id.equals("pour")) && ClientState.S.block.equals("stone") && !ClientState.S.recent.isEmpty() && false) t.setString("block", "water");
        t.setInteger("stroke", stroke);
        ClientState.remember(ClientState.S.block);
        ClientState.send("brush", t);
    }

    private static List<String> recent(int n) {
        List<String> out = new ArrayList<>();
        out.add(ClientState.S.block);
        for (String s : ClientState.S.recent) { if (out.size() >= n) break; if (!out.contains(s)) out.add(s); }
        return out;
    }

    private static String id(IBlockState s) {
        String id = s.getBlock().getRegistryName() == null ? "stone" : s.getBlock().getRegistryName().toString();
        int meta = s.getBlock().getMetaFromState(s);
        return meta == 0 ? id : id + ":" + meta;
    }

    private void pick(BlockPos at) {
        IBlockState s = mc.world.getBlockState(at);
        ClientState.S.block = id(s);
        ClientState.remember(ClientState.S.block);
        ClientState.save();
        ClientState.say("✦ Block: " + s.getBlock().getLocalizedName());
    }

    private void setTool(Tool t) {
        tool = t;
        ClientState.S.opts.put("tool", t.id);
        ClientState.save();
    }

    private static String size(BlockPos a, BlockPos b) {
        return (Math.abs(a.getX() - b.getX()) + 1) + "×" + (Math.abs(a.getY() - b.getY()) + 1) + "×" + (Math.abs(a.getZ() - b.getZ()) + 1);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    // ---------------------------------------------------------------- input

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (button == 0 || openMenu != null) { if (clickMenu(mx, my)) return; }
        if (showTools) { Tool t = toolAt(mx, my); if (t != null && button == 0) { setTool(t); return; } }
        Opt o = optAt(mx, my);
        if (o != null) { clickOpt(o, mx, button); return; }
        if (overUi(mx, my)) return;
        if (button == 0) {
            stroke = (int) (System.nanoTime() & 0x7FFFFFFF) | 1;
            lastDabAt = null;
            painting = true;
            dab(true);
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (state == 0) { painting = false; dragOpt = null; }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int w = Mouse.getEventDWheel();
        if (w == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth, my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (showTools && in(mx, my, toolsX(), top(), TOOLS_W, bottom() - top())) { toolScroll = Math.max(0, toolScroll + (w > 0 ? -22 : 22)); return; }
        Opt o = optAt(mx, my);
        if (o != null && o.kind.equals("int")) { setInt(o, ival(o.key) + (w > 0 ? 1 : -1) * Math.max(1, (o.max - o.min) / 100)); return; }
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) setInt(OPTS.get("size"), radius() + (w > 0 ? 1 : -1));
        else EditorMode.zoom(w);
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL);
        if (key == Keyboard.KEY_ESCAPE) { if (openMenu != null) openMenu = null; else if (showHelp) showHelp = false; else EditorMode.exit(); return; }
        if (key == StudioClient.EDITOR.getKeyCode()) { EditorMode.exit(); return; }
        if (ctrl && key == Keyboard.KEY_Z) { action("undo"); return; }
        if (ctrl && key == Keyboard.KEY_Y) { action("redo"); return; }
        if (ctrl && key == Keyboard.KEY_C) { action("copy"); return; }
        if (ctrl && key == Keyboard.KEY_V) { action("paste"); return; }
        if (key == Keyboard.KEY_TAB) { boolean any = showTools || showOptions; showTools = showOptions = !any; return; }
        if (key == Keyboard.KEY_F) { EditorMode.focusHover(); return; }
        if (key == Keyboard.KEY_B) { mc.displayGuiScreen(new StudioScreen(this)); return; }
        if (key == Keyboard.KEY_LBRACKET) { setInt(OPTS.get("size"), radius() - 1); return; }
        if (key == Keyboard.KEY_RBRACKET) { setInt(OPTS.get("size"), radius() + 1); return; }
        if (key == Keyboard.KEY_F1) showHelp = !showHelp;
    }
}
