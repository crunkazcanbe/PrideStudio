package com.dogpound.pridestudio.client;

import com.dogpound.pridestudio.OrthoCam;
import com.dogpound.pridestudio.PrideFrame;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.biome.Biome;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The Pride Studio panel: WorldEdit + VoxelSniper + Axiom tools as buttons, sliders and a block picker instead of
 * commands (requested feature). Left: pages. Middle: the page's tools. Right: the block picker. Bottom: result, undo/redo.
 */
public class StudioScreen extends GuiScreen {
    private final GuiScreen parent;
    public StudioScreen(GuiScreen parent) { this.parent = parent; }
    @Override public boolean doesGuiPauseGame() { return false; }

    static final String[][] TABS = {
            {"Fill & Water", "fill holes, lakes, drain, fix"}, {"Selection", "corners, grow, shrink, move"}, {"Region", "set, replace, walls, stack"},
            {"Shapes", "spheres, domes, cones, torus"}, {"Clipboard", "copy, paste, rotate, flip"}, {"Brushes", "VoxelSniper-style painting"},
            {"Nature", "trees, flowers, mobs, items"}, {"History", "undo, redo, every edit"}, {"Settings", "how the studio behaves"}};
    static final int[] ACCENT = {0xFF5BCEFA, 0xFFF5A9B8, 0xFFFF8C00, 0xFFFFED00, 0xFF8CE06A, 0xFFB57EDC, 0xFF3FBF6F, 0xFFE40303, 0xFFBBBBBB};

    // immediate-mode widgets: rebuilt every frame, clicked by rectangle
    private final List<Object[]> hits = new ArrayList<>();
    private String hint = "";
    private int oy, px, pw, pageTop, pageBottom, scroll, contentH;
    private String dragging;                    // slider being dragged
    private int dragX, dragW, dragMin, dragMax;

    // block picker
    private GuiTextField blockField, maskField, searchField;
    private static List<Object[]> allBlocks;    // {ItemStack, id, lowercase name}
    private final List<Object[]> shown = new ArrayList<>();
    private int gridX, gridY, gridCols, gridRows, gridScroll;
    private String lastSearch = null;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        blockField = field(blockField, ClientState.S.block);
        maskField = field(maskField, ClientState.S.mask);
        searchField = field(searchField, searchField == null ? "" : searchField.getText());
        if (allBlocks == null) buildBlockList();
    }

    private GuiTextField field(GuiTextField old, String text) {
        GuiTextField f = new GuiTextField(0, fontRenderer, 0, 0, 100, 14);
        f.setMaxStringLength(512);
        f.setText(old != null ? old.getText() : text);
        if (old != null) f.setFocused(old.isFocused());
        return f;
    }

    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); sync(); ClientState.save(); }

    private void sync() {
        ClientState.S.block = blockField.getText().trim();
        ClientState.S.mask = maskField.getText().trim();
    }

    // ------------------------------------------------------------------ the block list (every block item + water/lava/air)
    static void buildBlockList() {
        allBlocks = new ArrayList<>();
        allBlocks.add(new Object[]{ new ItemStack(Items.WATER_BUCKET), "water", "water" });
        allBlocks.add(new Object[]{ new ItemStack(Items.LAVA_BUCKET), "lava", "lava" });
        allBlocks.add(new Object[]{ new ItemStack(Blocks.BARRIER), "air", "air (empty)" });
        for (Item it : Item.REGISTRY) {
            if (!(it instanceof ItemBlock)) continue;
            NonNullList<ItemStack> l = NonNullList.create();
            try { it.getSubItems(CreativeTabs.SEARCH, l); } catch (Throwable ignored) { }
            if (l.isEmpty()) l.add(new ItemStack(it));
            for (ItemStack s : l) {
                try {
                    Block b = ((ItemBlock) it).getBlock();
                    int meta = it.getMetadata(s.getMetadata());
                    String id = b.getRegistryName() + (meta != 0 ? ":" + meta : "");
                    if (id.startsWith("minecraft:")) id = id.substring(10);
                    allBlocks.add(new Object[]{ s, id, (s.getDisplayName() + " " + id).toLowerCase(Locale.ROOT) });
                } catch (Throwable ignored) { }
            }
        }
    }

    static String idOf(ItemStack s) {
        if (s.getItem() == Items.WATER_BUCKET) return "water";
        if (s.getItem() == Items.LAVA_BUCKET) return "lava";
        if (!(s.getItem() instanceof ItemBlock)) return null;
        Block b = ((ItemBlock) s.getItem()).getBlock();
        int meta = s.getItem().getMetadata(s.getMetadata());
        String id = b.getRegistryName() + (meta != 0 ? ":" + meta : "");
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    @SuppressWarnings("deprecation")
    static String idOf(IBlockState st) {
        Block b = st.getBlock();
        int meta = b.getMetaFromState(st);
        String id = b.getRegistryName() + (meta != 0 ? ":" + meta : "");
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    // ------------------------------------------------------------------ drawing
    @Override
    public void drawScreen(int mx, int my, float pt) {
        hits.clear(); hint = "";
        sync();
        PrideFrame f = PrideFrame.fit(width, height);
        String sel = ClientState.pos1 != null && ClientState.pos2 != null ? sizeText() : "no selection";
        f.draw(this, "Pride Studio", "§7" + sel + "   §d↶ " + ClientState.history.size() + "  ↷ " + ClientState.redo);

        // left: page tabs
        int tx = f.cx, tw = Math.min(118, Math.max(84, f.cw / 7)), ty = f.cy;
        int th = Math.max(18, Math.min(30, (f.ch - 48) / TABS.length - 3));
        for (int i = 0; i < TABS.length; i++) {
            boolean over = in(mx, my, tx, ty, tw, th), on = ClientState.S.tab == i;
            PrideFrame.tile(tx, ty, tw, th, ACCENT[i], over, on);
            fontRenderer.drawStringWithShadow(TABS[i][0], tx + 6, ty + (th > 24 ? 5 : (th - 8) / 2f), on ? 0xFFFFFF : 0xD8D0E8);
            if (th > 24) fontRenderer.drawString(trim(TABS[i][1], tw - 10), tx + 6, ty + 16, 0x8A8499);
            final int k = i;
            hit(tx, ty, tw, th, () -> { ClientState.S.tab = k; scroll = 0; }, TABS[i][1]);
            ty += th + 3;
        }

        // right: block picker
        int pickW = Math.max(150, Math.min(232, f.cw / 4));
        int pickX = f.cx + f.cw - pickW;
        drawPicker(mx, my, pickX, f.cy, pickW, f.ch - 46);

        // middle: the page (scrolls)
        px = tx + tw + 10; pw = pickX - 10 - px;
        pageTop = f.cy; pageBottom = f.cy + f.ch - 46;
        PrideFrame.card(px - 4, pageTop - 2, pw + 8, pageBottom - pageTop + 4, ACCENT[ClientState.S.tab]);
        PrideFrame.clip(px - 4, pageTop, pw + 8, pageBottom - pageTop);
        oy = pageTop + 6 - scroll;
        int start = hits.size();
        page(mx, my, ClientState.S.tab);
        contentH = oy + scroll - pageTop;
        PrideFrame.unclip();
        // hits outside the visible page area must not be clickable
        for (int i = hits.size() - 1; i >= start; i--) { Object[] h = hits.get(i); int y = (Integer) h[1], hh = (Integer) h[3]; if (y + hh < pageTop || y > pageBottom) hits.remove(i); }
        PrideFrame.scrollbar(px + pw + 1, pageTop, pageBottom - pageTop, scroll, pageBottom - pageTop, contentH);

        // bottom bar: result / hint, undo, redo, where-tools-work, done
        int by = f.cy + f.ch - 38;
        Gui.drawRect(f.cx, by - 4, f.cx + f.cw, by - 3, 0x40FFFFFF);
        String line = !hint.isEmpty() ? "§7" + hint : System.currentTimeMillis() - ClientState.msgAt < 15000 ? ClientState.msg : "§8Tip: hover any button to see what it does.";
        fontRenderer.drawStringWithShadow(trim(line, f.cw - 10), f.cx + 2, by, 0xFFFFFF);
        int bx = f.cx, bw = 74, bh = 18, bt = by + 14;
        button(mx, my, bx, bt, bw, bh, "↶ Undo", 0xFF3D2168, () -> ClientState.send("undo", new NBTTagCompound()), "Undo your last edit (Ctrl+Z in the world)"); bx += bw + 4;
        button(mx, my, bx, bt, bw, bh, "↷ Redo", 0xFF3D2168, () -> ClientState.send("redo", new NBTTagCompound()), "Redo what you undid (Ctrl+Y)"); bx += bw + 4;
        button(mx, my, bx, bt, 96, bh, ClientState.S.preview ? "\u00A7aPreview ON" : "Preview off", ClientState.S.preview ? 0xFF2A5A3A : PrideFrame.BUTTON,
                () -> ClientState.S.preview = !ClientState.S.preview, "Preview ON: tools show a see-through ghost first; Enter builds it, Backspace cancels"); bx += 100;
        boolean look = ClientState.S.workAt.equals("look");
        button(mx, my, bx, bt, 150, bh, look ? "Tools work: where I look" : "Tools work: where I stand", look ? 0xFF24408E : 0xFF2A5A3A,
                () -> ClientState.S.workAt = look ? "feet" : "look", "Fill, shapes and paste happen at your feet, or at the block you're looking at"); bx += 154;
        button(mx, my, bx, bt, 64, bh, "Wand", PrideFrame.BUTTON, () -> give("wand"), "Get the Studio Wand: left click = corner 1, right click = corner 2"); bx += 68;
        button(mx, my, bx, bt, 64, bh, "Brush", PrideFrame.BUTTON, () -> give("brush"), "Get the Studio Brush: right click paints, left click reverses");
        button(mx, my, f.cx + f.cw - 80, bt, 80, bh, "✔ Done", PrideFrame.BUTTON, () -> mc.displayGuiScreen(parent), "Close the panel (\\ opens it again)");

        super.drawScreen(mx, my, pt);
    }

    private String sizeText() {
        BlockPos a = ClientState.pos1, b = ClientState.pos2;
        long x = Math.abs(a.getX() - b.getX()) + 1, y = Math.abs(a.getY() - b.getY()) + 1, z = Math.abs(a.getZ() - b.getZ()) + 1;
        return x + "×" + y + "×" + z + " (" + (x * y * z) + ")";
    }

    private String trim(String s, int w) { return fontRenderer.getStringWidth(s) <= w ? s : fontRenderer.trimStringToWidth(s, w - 8) + "…"; }

    private void give(String item) { NBTTagCompound t = new NBTTagCompound(); t.setString("item", item); ClientState.send("give", t); }

    // ------------------------------------------------------------------ pages
    private void page(int mx, int my, int tab) {
        switch (tab) {
            case 0: pageFill(mx, my); break;
            case 1: pageSelection(mx, my); break;
            case 2: pageRegion(mx, my); break;
            case 3: pageShapes(mx, my); break;
            case 4: pageClipboard(mx, my); break;
            case 5: pageBrushes(mx, my); break;
            case 6: pageNature(mx, my); break;
            case 7: pageHistory(mx, my); break;
            default: pageSettings(mx, my); break;
        }
    }

    private void pageFill(int mx, int my) {
        heading("Fill & Water", "WorldEdit's //fill done smart: fills only the empty spots, from the top layer down, never above where it starts.");
        slider(mx, my, "Radius", "radius", 1, 128, "How far sideways the fill/drain may spread");
        slider(mx, my, "Depth", "depth", 1, 128, "How many layers down Fill may go");
        slider(mx, my, "Size", "size", 1, 64, "Size for Remove near / above / below and Replace near");
        slider(mx, my, "Height", "height", 1, 128, "How tall Remove above / below reaches");
        sub("Fill");
        grid(mx, my, 3, new Object[][]{
                {"Fill", op("fill"), "Fill the hole you're standing in with your block (spreads on the top layer, then down)"},
                {"Deep fill", op("fillr"), "Fill that spreads sideways on every layer (caves under the hole too)"},
                {"Fill water", (Runnable) () -> runWith("fill", "block", "water"), "Fill with still water: instant ponds and lakes"},
                {"Fill lava", (Runnable) () -> runWith("fill", "block", "lava"), "Fill with still lava"},
                {"Drain", op("drain"), "Remove water and lava connected to you, within the radius"},
                {"Fix water", op("fixwater"), "Turn flowing water into calm still water"},
                {"Fix lava", op("fixlava"), "Turn flowing lava into still lava"}});
        sub("Clear & replace");
        grid(mx, my, 3, new Object[][]{
                {"Remove near", op("removenear"), "Remove the mask block (or your block) within Size"},
                {"Remove above", op("removeabove"), "Clear everything above you (Size wide, Height tall)"},
                {"Remove below", op("removebelow"), "Clear everything below you (Size wide, Height deep)"},
                {"Replace near", op("replacenear"), "Swap the mask block for your block within Size"}});
        sub("Weather & touch-ups");
        grid(mx, my, 3, new Object[][]{
                {"Snow", op("snow"), "Snow layers and ice on top of everything in the radius"},
                {"Thaw", op("thaw"), "Melt snow and ice in the radius"},
                {"Green", op("green"), "Turn dirt into grass in the radius"},
                {"Put out fire", op("ex"), "Put out fires and burning players in the radius"}});
    }

    private void pageSelection(int mx, int my) {
        heading("Selection", "Two corners make a box. Use the Wand, the [ and ] keys, or these buttons.");
        BlockPos a = ClientState.pos1, b = ClientState.pos2;
        info("Corner 1: " + (a == null ? "§8not set" : "§d" + a.getX() + ", " + a.getY() + ", " + a.getZ()));
        info("Corner 2: " + (b == null ? "§8not set" : "§b" + b.getX() + ", " + b.getY() + ", " + b.getZ()));
        info("Size: " + (a != null && b != null ? "§f" + sizeText() + " blocks" : "§8pick both corners"));
        sub("Pick corners");
        grid(mx, my, 3, new Object[][]{
                {"Corner 1 here", (Runnable) () -> ClientState.run("pos1", null, false), "Corner 1 at your feet"},
                {"Corner 2 here", (Runnable) () -> ClientState.run("pos2", null, false), "Corner 2 at your feet"},
                {"Corner 1 look", (Runnable) () -> lookCorner("pos1"), "Corner 1 on the block you're looking at"},
                {"Corner 2 look", (Runnable) () -> lookCorner("pos2"), "Corner 2 on the block you're looking at"},
                {"This chunk", op("selchunk"), "Select the whole chunk you're in, bedrock to sky"},
                {"Full height", op("fullheight"), "Stretch the selection from bedrock to the sky"},
                {"Count blocks", op("count"), "Count solid blocks (or the mask block) in the selection"},
                {"Clear", op("selclear"), "Forget both corners"}});
        sub("Grow, shrink, move");
        slider(mx, my, "Amount", "amount", 1, 64, "How many blocks to grow, shrink or move by");
        chips(mx, my, "Direction", new String[]{"look", "up", "down", "north", "south", "east", "west", "sides", "all"}, () -> ClientState.S.dir, v -> ClientState.S.dir = v,
                "Which way: where you look, a compass side, all 4 sides, or every side");
        grid(mx, my, 3, new Object[][]{
                {"Expand", op("expand"), "Grow the box in that direction"},
                {"Shrink", op("contract"), "Shrink the box from that side"},
                {"Move box", op("shift"), "Move the box itself (not the blocks)"}});
        toggle(mx, my, "Show the selection box in the world", () -> ClientState.S.showBox, () -> ClientState.S.showBox = !ClientState.S.showBox);
    }

    private void pageRegion(int mx, int my) {
        heading("Region", "Everything here works on the selected box. Pick the block on the right; the mask is \"what to replace\".");
        sub("Fill the box");
        grid(mx, my, 3, new Object[][]{
                {"Set", op("set"), "Fill the whole box with your block (mixes like 50%stone,50%dirt work)"},
                {"Replace", op("replace"), "Turn the mask block into your block, everywhere in the box"},
                {"Clear", op("clear"), "Empty the box (air)"},
                {"Walls", op("walls"), "Four walls around the box"},
                {"Outline", op("outline"), "All six sides: walls, floor and ceiling"},
                {"Hollow", op("hollow"), "Empty the inside, keeping a shell (Thickness)"},
                {"Overlay", op("overlay"), "Put your block on top of the ground (Layers thick)"},
                {"Center", op("center"), "Mark the middle of the box"},
                {"Line", op("line"), "A straight line from corner 1 to corner 2 (Thickness)"}});
        slider(mx, my, "Thickness", "thick", 1, 16, "Shell for Hollow, width of Line");
        slider(mx, my, "Layers", "depth", 1, 16, "How thick Overlay is");
        sub("Terrain");
        grid(mx, my, 3, new Object[][]{
                {"Smooth", op("smooth"), "Soften the ground in the box (Times = passes)"},
                {"Naturalize", op("naturalize"), "Grass on top, 3 dirt, stone below"},
                {"Drain", op("drainsel"), "Remove all water and lava in the box"}});
        sub("Copy the box around");
        slider(mx, my, "Times / distance", "amount", 1, 64, "Stack copies, Move distance, Smooth passes");
        chips(mx, my, "Direction", new String[]{"look", "up", "down", "north", "south", "east", "west"}, () -> ClientState.S.dir, v -> ClientState.S.dir = v, "Which way Stack and Move go");
        grid(mx, my, 3, new Object[][]{
                {"Stack", op("stack"), "Repeat the box next to itself (Times)"},
                {"Move", op("move"), "Move the blocks (Distance)"}});
        toggle(mx, my, "Skip air when stacking / moving", () -> ClientState.S.skipAir, () -> ClientState.S.skipAir = !ClientState.S.skipAir);
        toggle(mx, my, "The selection moves with the blocks", () -> ClientState.S.moveSel, () -> ClientState.S.moveSel = !ClientState.S.moveSel);
        sub("Biome");
        stepper(mx, my, "Biome: §f" + ClientState.S.biome, () -> cycleBiome(-1), () -> cycleBiome(1), "Pick the biome to paint");
        grid(mx, my, 3, new Object[][]{{"Set biome", op("setbiome"), "Change the biome of every column in the box (not undoable)"}});
    }

    private void cycleBiome(int d) {
        List<String> names = new ArrayList<>();
        for (Biome b : Biome.REGISTRY) names.add(b.getRegistryName().toString().replace("minecraft:", ""));
        if (names.isEmpty()) return;
        int i = names.indexOf(ClientState.S.biome);
        ClientState.S.biome = names.get(((i + d) % names.size() + names.size()) % names.size());
    }

    static final String[][] SHAPES = {{"sphere", "A ball"}, {"dome", "Top half of a ball"}, {"bowl", "Bottom half of a ball"}, {"ellipsoid", "A stretched ball (Radius × Height × Second radius)"},
            {"cylinder", "A tower (Radius, Height)"}, {"disc", "A flat circle"}, {"cone", "A cone (Radius, Height)"}, {"pyramid", "A pyramid (Height = size)"},
            {"cube", "A cube (Radius)"}, {"torus", "A donut (Radius, Second radius = tube)"}, {"arch", "An arch (Radius, Second = thickness, Height = depth)"}, {"helix", "A spiral (Radius, Height, Turns)"}};

    private void pageShapes(int mx, int my) {
        heading("Shapes", "Pick a shape, set its size, then Build. Hollow makes a shell.");
        List<String> keys = new ArrayList<>(); for (String[] s : SHAPES) keys.add(s[0]);
        chips(mx, my, "Shape", keys.toArray(new String[0]), () -> ClientState.S.shape, v -> ClientState.S.shape = v, "Which shape to build");
        for (String[] s : SHAPES) if (s[0].equals(ClientState.S.shape)) info("§7" + s[1]);
        slider(mx, my, "Radius", "radius", 1, 64, "How wide");
        slider(mx, my, "Height", "height", 1, 128, "How tall (cylinder, cone, pyramid, helix, ellipsoid)");
        slider(mx, my, "Second radius / tube", "thick", 1, 32, "Torus tube, arch thickness, ellipsoid depth, helix thickness");
        slider(mx, my, "Turns", "amount", 1, 16, "How many times the helix goes round");
        toggle(mx, my, "Hollow", () -> ClientState.S.hollow, () -> ClientState.S.hollow = !ClientState.S.hollow);
        grid(mx, my, 2, new Object[][]{
                {"✦ Build " + ClientState.S.shape, op("shape"), "Build it (at your feet or where you look, see the bottom bar)"},
                {"Build where I look", (Runnable) () -> ClientState.run("shape", null, true), "Build it on the block you're looking at"}});
    }

    private void pageClipboard(int mx, int my) {
        heading("Clipboard", "Copy remembers where you stood, so Paste lands the same way round relative to you.");
        info("Clipboard: " + (ClientState.clip == 0 ? "§8empty" : "§f" + ClientState.clip + " blocks"));
        grid(mx, my, 3, new Object[][]{
                {"Copy", op("copy"), "Copy the selection (Ctrl+C in the world)"},
                {"Cut", op("cut"), "Copy, then clear the selection"},
                {"Paste", op("paste"), "Paste (Ctrl+V in the world)"},
                {"Paste where I look", (Runnable) () -> ClientState.run("paste", null, true), "Paste at the block you're looking at"},
                {"Empty clipboard", op("clipclear"), "Forget the clipboard"}});
        toggle(mx, my, "Skip air when pasting", () -> ClientState.S.skipAir, () -> ClientState.S.skipAir = !ClientState.S.skipAir);
        sub("Turn & mirror");
        grid(mx, my, 4, new Object[][]{
                {"↻ 90°", (Runnable) () -> runWith("rotate", "deg", 90), "Turn the clipboard a quarter clockwise"},
                {"↻ 180°", (Runnable) () -> runWith("rotate", "deg", 180), "Turn it round"},
                {"↺ 90°", (Runnable) () -> runWith("rotate", "deg", 270), "Turn a quarter anticlockwise"},
                {"Flip (look)", (Runnable) () -> runWith("flip", "axis", ""), "Mirror the way you're looking"},
                {"Flip X", (Runnable) () -> runWith("flip", "axis", "x"), "Mirror east ↔ west"},
                {"Flip Z", (Runnable) () -> runWith("flip", "axis", "z"), "Mirror north ↔ south"},
                {"Flip Y", (Runnable) () -> runWith("flip", "axis", "y"), "Upside down"}});
    }

    static final String[][] BRUSHES = {
            {"ball", "A ball of your block (left click: carve a ball out)"}, {"disc", "A flat circle"}, {"cylinder", "A circle Height tall"},
            {"cube", "A cube"}, {"snipe", "One block on the face you click (left click: remove that block)"}, {"splatter", "A speckled ball (Density %)"},
            {"paint", "Recolour existing blocks only, never adds"}, {"overlay", "Repaint the top Height blocks of the ground (left: add a layer)"},
            {"blend", "Smooth bumps and holes by majority vote"}, {"erode", "Wear away exposed blocks (Strength = how exposed)"},
            {"fillin", "Fill nooks and dents (left click: erode)"}, {"raise", "Lift the ground in a soft hill (left: lower)"},
            {"lower", "Push the ground down (left: raise)"}, {"flatten", "Cut above and fill below the clicked height"},
            {"filldown", "Pour your block down into every gap below"}, {"drain", "Remove water and lava"}, {"snow", "Snow it (left: thaw)"},
            {"tree", "Grow a tree (pick the type in Nature)"}, {"jagged", "A rough rock spike out of the face"}, {"line", "A line from your eyes to the block"}};

    private void pageBrushes(int mx, int my) {
        heading("Brushes", "Hold the Studio Brush: right click paints from far away, left click does the reverse. - / = change size.");
        List<String> keys = new ArrayList<>(); for (String[] s : BRUSHES) keys.add(s[0]);
        chips(mx, my, "Brush", keys.toArray(new String[0]), () -> ClientState.S.brush, v -> ClientState.S.brush = v, "Which brush the Studio Brush uses");
        for (String[] s : BRUSHES) if (s[0].equals(ClientState.S.brush)) info("§7" + s[1]);
        slider(mx, my, "Brush size", "size", 0, 32, "Radius of the brush");
        slider(mx, my, "Height", "height", 1, 64, "Cylinder height, overlay depth, flatten reach, spike length");
        slider(mx, my, "Density %", "density", 1, 100, "How full the splatter is; flower/forest density too");
        slider(mx, my, "Strength", "strength", 1, 8, "How strong raise/lower is; how exposed a block must be to erode");
        slider(mx, my, "Reach", "range", 8, 300, "How far the brush and wand reach");
        toggle(mx, my, "Only change blocks matching the mask", () -> ClientState.S.useMask, () -> ClientState.S.useMask = !ClientState.S.useMask);
        toggle(mx, my, "Show the brush target in the world", () -> ClientState.S.showTarget, () -> ClientState.S.showTarget = !ClientState.S.showTarget);
        grid(mx, my, 3, new Object[][]{
                {"Give me the brush", (Runnable) () -> give("brush"), "Put a Studio Brush in your inventory"},
                {"Paint where I look", (Runnable) () -> brushNow(false), "Use the brush once at the crosshair"},
                {"Reverse where I look", (Runnable) () -> brushNow(true), "Use the reverse once at the crosshair"}});
    }

    private void brushNow(boolean alt) {
        RayTraceResult r = ClientState.look();
        if (r == null) { ClientState.say("§cNothing in reach where you're looking."); return; }
        NBTTagCompound t = ClientState.args();
        ClientState.at(t, r.getBlockPos());
        t.setInteger("face", r.sideHit.getIndex());
        t.setBoolean("alt", alt);
        ClientState.send("brush", t);
    }

    static final String[] TREES = {"oak", "birch", "tallbirch", "spruce", "pine", "big", "jungle", "megajungle", "acacia", "darkoak", "swamp", "mixed"};

    private void pageNature(int mx, int my) {
        heading("Nature", "Plant forests and flowers, smooth hills, and clear mobs or dropped items around you.");
        chips(mx, my, "Trees", TREES, () -> ClientState.S.tree, v -> ClientState.S.tree = v, "Tree type for Forest and the tree brush");
        slider(mx, my, "Radius", "radius", 1, 64, "How far around you");
        slider(mx, my, "Density %", "density", 1, 100, "How many columns get a tree or flower");
        slider(mx, my, "Smooth passes", "amount", 1, 16, "How soft Smooth nearby makes it");
        grid(mx, my, 3, new Object[][]{
                {"Plant forest", op("forest"), "Trees on grass and dirt within the radius"},
                {"Flowers & grass", op("flora"), "Tall grass and flowers on grass blocks"},
                {"Smooth nearby", op("smoothnear"), "Soften the ground around you"},
                {"Naturalize nearby", op("naturalizenear"), "Grass, dirt, stone layers around you"},
                {"Snow", op("snow"), "Snow it"}, {"Thaw", op("thaw"), "Melt it"}, {"Green", op("green"), "Dirt to grass"}});
        sub("Clean up");
        toggle(mx, my, "Remove every mob (not just monsters) — pets and named mobs are always kept", () -> ClientState.S.butcherAll, () -> ClientState.S.butcherAll = !ClientState.S.butcherAll);
        grid(mx, my, 3, new Object[][]{
                {"Remove mobs", op("butcher"), "Remove monsters (or every mob) within the radius (not undoable)"},
                {"Remove items", op("removeitems"), "Remove dropped items and XP orbs within the radius"}});
    }

    private void pageHistory(int mx, int my) {
        heading("History", "Your last 50 edits, newest first. Undo puts back every block, chests and machines included.");
        grid(mx, my, 4, new Object[][]{
                {"↶ Undo", op("undo"), "Undo the newest edit"},
                {"↷ Redo", op("redo"), "Redo"},
                {"↶ Undo 5", (Runnable) () -> runWith("undo", "times", 5), "Undo the last five edits"},
                {"↷ Redo 5", (Runnable) () -> runWith("redo", "times", 5), "Redo five"},
                {"Clear history", op("clearhist"), "Forget all undo steps"},
                {"Refresh", op("sync"), "Ask the server for the latest list"}});
        info("§7Redo waiting: §f" + ClientState.redo);
        List<String> h = ClientState.history;
        if (h.isEmpty()) info("§8Nothing yet — every button that changes blocks shows up here.");
        for (int i = h.size() - 1; i >= 0; i--) {
            int n = h.size() - i;
            Gui.drawRect(px, oy, px + pw - 6, oy + 14, n % 2 == 0 ? 0x30FFFFFF : 0x18FFFFFF);
            fontRenderer.drawStringWithShadow("§d" + n + ".§f " + h.get(i), px + 4, oy + 3, 0xFFFFFF);
            final int steps = n;
            if (n > 1) { int bx = px + pw - 6 - 66; button(mx, my, bx, oy, 66, 14, "undo to here", PrideFrame.BUTTON, () -> runWith("undo", "times", steps), "Undo this edit and everything after it"); }
            oy += 16;
        }
    }

    private void pageSettings(int mx, int my) {
        heading("Settings", "How Pride Studio behaves. Saved in config/pridestudio-editor.json.");
        chips(mx, my, "Tools work", new String[]{"feet", "look"}, () -> ClientState.S.workAt, v -> ClientState.S.workAt = v, "feet = where you stand, look = the block you're looking at");
        chips(mx, my, "Box colour", new String[]{"rainbow", "pink", "blue", "white", "green", "gold"}, () -> ClientState.S.boxColor, v -> ClientState.S.boxColor = v, "Colour of the selection box in the world");
        toggle(mx, my, "Show the selection box", () -> ClientState.S.showBox, () -> ClientState.S.showBox = !ClientState.S.showBox);
        toggle(mx, my, "Show the brush target", () -> ClientState.S.showTarget, () -> ClientState.S.showTarget = !ClientState.S.showTarget);
        toggle(mx, my, "Show the tool line on screen while holding the wand/brush", () -> ClientState.S.showHud, () -> ClientState.S.showHud = !ClientState.S.showHud);
        toggle(mx, my, "Close the panel after using a tool", () -> ClientState.S.closeAfter, () -> ClientState.S.closeAfter = !ClientState.S.closeAfter);
        toggle(mx, my, "Builder menu: press once to open/close (instead of holding Alt)", () -> ClientState.S.menuToggle, () -> ClientState.S.menuToggle = !ClientState.S.menuToggle);
        toggle(mx, my, "Builder menu works outside creative too", () -> ClientState.S.menuSurvival, () -> ClientState.S.menuSurvival = !ClientState.S.menuSurvival);
        sub("Builder switches");
        for (String[] c : Caps.ALL) toggle(mx, my, c[1] + " \u00A78\u2014 " + c[2], () -> Caps.on(c[0]), () -> Caps.toggle(c[0]));
        slider(mx, my, "Flight speed %", "flight", 10, 999, "Creative flight speed (100 = normal)");
        stepper(mx, my, "Builder menu size: \u00A7f" + (ClientState.S.menuScale <= 0 ? "automatic" : ClientState.S.menuScale + "x"),
                () -> ClientState.S.menuScale = ClientState.S.menuScale <= 1F ? 0F : ClientState.S.menuScale - 0.25F,
                () -> ClientState.S.menuScale = Math.min(4F, (ClientState.S.menuScale <= 0 ? 1F : ClientState.S.menuScale) + 0.25F), "How big the Alt menu is (automatic fits your screen)");
        toggle(mx, my, "Click sounds", () -> ClientState.S.sounds, () -> ClientState.S.sounds = !ClientState.S.sounds);
        slider(mx, my, "Reach", "range", 8, 300, "How far the wand, brush and \"where I look\" reach");
        grid(mx, my, 3, new Object[][]{
                {"Camera", (Runnable) () -> mc.displayGuiScreen(new OrthoCam.Screen(this)), "The orthographic build camera's settings"},
                {"Keys", (Runnable) () -> mc.displayGuiScreen(new GuiControls(this, mc.gameSettings)), "Change Pride Studio's keys (in the Pride Studio group)"},
                {"Reset sliders", (Runnable) () -> ClientState.S.vals.clear(), "Put every slider back to its default"}});
        sub("Keys");
        for (String s : new String[]{"\\  open this panel", "Hold Left Alt  builder menu (saved hotbars, switches, flight speed)", "[  ]  corner 1 / 2 at the crosshair", "Ctrl+Z / Ctrl+Y  undo / redo", "Ctrl+C / Ctrl+V  copy / paste",
                "- / =  brush smaller / bigger (holding the brush)", "Numpad 4  orthographic camera on/off", "Esc menu  ✦ Pride Studio button", "/ps <tool> key=value  (command backup)"})
            info("§7" + s);
    }

    // ------------------------------------------------------------------ the block picker
    private void drawPicker(int mx, int my, int x, int y, int w, int h) {
        PrideFrame.card(x, y - 2, w, h + 4, PrideFrame.PINK);
        int ix = x + 6, iw = w - 12, cy = y + 4;
        fontRenderer.drawStringWithShadow("§lBlock to place", ix, cy, 0xFFFFFF); cy += 11;
        place(blockField, ix, cy, iw); blockField.drawTextBox(); cy += 17;
        int bw = (iw - 4) / 2;
        button(mx, my, ix, cy, bw, 14, "In my hand", PrideFrame.BUTTON, () -> {
            String id = idOf(mc.player.getHeldItemMainhand()); if (id != null) blockField.setText(id); else ClientState.say("§cHold a block first.");
        }, "Use the block in your hand");
        button(mx, my, ix + bw + 4, cy, bw, 14, "Looking at", PrideFrame.BUTTON, () -> {
            RayTraceResult r = ClientState.look(); if (r != null) blockField.setText(idOf(mc.world.getBlockState(r.getBlockPos())));
        }, "Use the block you're looking at"); cy += 18;
        fontRenderer.drawStringWithShadow("§lMask §7(what to replace)", ix, cy, 0xFFFFFF); cy += 11;
        place(maskField, ix, cy, iw); maskField.drawTextBox(); cy += 17;
        button(mx, my, ix, cy, bw, 14, ClientState.S.useMask ? "§aMask on" : "Mask off", ClientState.S.useMask ? 0xFF2A5A3A : PrideFrame.BUTTON,
                () -> ClientState.S.useMask = !ClientState.S.useMask, "Brushes and Set only change blocks that match the mask");
        button(mx, my, ix + bw + 4, cy, bw, 14, "Mask = look", PrideFrame.BUTTON, () -> {
            RayTraceResult r = ClientState.look(); if (r != null) maskField.setText(idOf(mc.world.getBlockState(r.getBlockPos())));
        }, "Set the mask to the block you're looking at"); cy += 18;

        // recent blocks
        if (!ClientState.S.recent.isEmpty()) {
            fontRenderer.drawString("§7Recent", ix, cy, 0xFFFFFF); cy += 10;
            int rx = ix;
            for (String id : ClientState.S.recent) {
                if (rx + 18 > ix + iw) break;
                ItemStack st = stackFor(id);
                boolean over = in(mx, my, rx, cy, 18, 18);
                Gui.drawRect(rx, cy, rx + 18, cy + 18, over ? 0x60FFFFFF : 0x30FFFFFF);
                if (st != null) item(st, rx + 1, cy + 1);
                hit(rx, cy, 18, 18, () -> blockField.setText(id), id);
                rx += 19;
            }
            cy += 22;
        }

        fontRenderer.drawString("§7Search every block (shift+click mixes, right click = mask)", ix, cy, 0xFFFFFF);
        cy += 10;
        place(searchField, ix, cy, iw); searchField.drawTextBox(); cy += 18;
        String q = searchField.getText().trim().toLowerCase(Locale.ROOT);
        if (!q.equals(lastSearch)) {
            lastSearch = q; gridScroll = 0; shown.clear();
            for (Object[] b : allBlocks) if (q.isEmpty() || ((String) b[2]).contains(q)) shown.add(b);
        }
        gridX = ix; gridY = cy; gridCols = Math.max(1, iw / 18); gridRows = Math.max(1, (y + h - cy - 4) / 18);
        int first = gridScroll * gridCols;
        Object[] tip = null;
        for (int i = 0; i < gridCols * gridRows && first + i < shown.size(); i++) {
            Object[] b = shown.get(first + i);
            int gx = gridX + (i % gridCols) * 18, gy = gridY + (i / gridCols) * 18;
            boolean over = in(mx, my, gx, gy, 18, 18);
            boolean chosen = b[1].equals(ClientState.S.block);
            if (over || chosen) Gui.drawRect(gx, gy, gx + 18, gy + 18, chosen ? 0x80F5A9B8 : 0x50FFFFFF);
            item((ItemStack) b[0], gx + 1, gy + 1);
            if (over) tip = b;
        }
        PrideFrame.scrollbar(ix + iw + 2, gridY, gridRows * 18, gridScroll, gridRows, (shown.size() + gridCols - 1) / gridCols);
        if (tip != null) hint = ((ItemStack) tip[0]).getDisplayName() + "  §8" + tip[1];
    }

    private static ItemStack stackFor(String id) {
        if (allBlocks == null) return null;
        for (Object[] b : allBlocks) if (b[1].equals(id)) return (ItemStack) b[0];
        return null;
    }

    private void place(GuiTextField f, int x, int y, int w) { f.x = x; f.y = y; f.width = w; f.height = 14; }

    private void item(ItemStack s, int x, int y) {
        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        try { itemRender.renderItemAndEffectIntoGUI(s, x, y); } catch (Throwable ignored) { }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------------ widgets
    private Runnable op(String o) {
        return () -> {
            if (ClientState.S.preview && Preview.previewable(o)) { Preview.arm(o); mc.displayGuiScreen(parent); }
            else ClientState.run(o, null);
        };
    }

    private void runWith(String o, String k, Object v) {
        NBTTagCompound t = new NBTTagCompound();
        if (v instanceof Integer) t.setInteger(k, (Integer) v); else t.setString(k, String.valueOf(v));
        ClientState.run(o, t);
    }

    private void lookCorner(String o) {
        RayTraceResult r = ClientState.look();
        if (r == null) { ClientState.say("§cNothing in reach where you're looking."); return; }
        NBTTagCompound t = new NBTTagCompound(); ClientState.at(t, r.getBlockPos()); ClientState.send(o, t);
    }

    private void heading(String title, String text) {
        fontRenderer.drawStringWithShadow("§l" + title, px, oy, ACCENT[ClientState.S.tab] & 0xFFFFFF);
        oy += 12;
        for (String l : fontRenderer.listFormattedStringToWidth(text, pw - 8)) { fontRenderer.drawString(l, px, oy, 0xA79FBF); oy += 10; }
        oy += 4;
    }

    private void sub(String t) {
        oy += 3;
        fontRenderer.drawStringWithShadow(t, px, oy, 0xF5A9B8);
        Gui.drawRect(px + fontRenderer.getStringWidth(t) + 6, oy + 4, px + pw - 8, oy + 5, 0x30FFFFFF);
        oy += 13;
    }

    private void info(String t) { fontRenderer.drawStringWithShadow(trim(t, pw - 8), px, oy, 0xFFFFFF); oy += 11; }

    private void grid(int mx, int my, int cols, Object[][] items) {
        int gap = 4, w = (pw - 8 - (cols - 1) * gap) / cols, bh = 18;
        for (int i = 0; i < items.length; i++) {
            int x = px + (i % cols) * (w + gap), y = oy + (i / cols) * (bh + gap);
            button(mx, my, x, y, w, bh, trim((String) items[i][0], w - 4), PrideFrame.BUTTON, (Runnable) items[i][1], (String) items[i][2]);
        }
        oy += ((items.length + cols - 1) / cols) * (bh + gap) + 2;
    }

    private void button(int mx, int my, int x, int y, int w, int h, String label, int color, Runnable r, String tip) {
        boolean over = PrideFrame.button(x, y, w, h, label, color, mx, my);
        if (over && tip != null) hint = tip;
        hits.add(new Object[]{ x, y, w, h, r, Boolean.TRUE });
    }

    private void hit(int x, int y, int w, int h, Runnable r, String tip) {
        hits.add(new Object[]{ x, y, w, h, r, Boolean.FALSE });
        if (tip != null && in(lastMx, lastMy, x, y, w, h)) hint = tip;
    }

    private void toggle(int mx, int my, String label, BooleanSupplier v, Runnable r) {
        boolean on = v.getAsBoolean();
        if (in(mx, my, px, oy, pw - 8, 16)) Gui.drawRect(px - 2, oy - 1, px + pw - 8, oy + 15, 0x30FFFFFF);
        Gui.drawRect(px, oy + 4, px + 16, oy + 12, on ? 0xFF8CE06A : 0xFF3D2168);
        Gui.drawRect(on ? px + 9 : px + 1, oy + 5, on ? px + 15 : px + 7, oy + 11, 0xFFFFFFFF);
        fontRenderer.drawStringWithShadow(trim(label, pw - 34), px + 22, oy + 4, on ? 0xFFFFFF : 0xA79FBF);
        hits.add(new Object[]{ px, oy, pw - 8, 16, r, Boolean.TRUE });
        oy += 18;
    }

    private void stepper(int mx, int my, String label, Runnable minus, Runnable plus, String tip) {
        button(mx, my, px, oy, 18, 15, "<", PrideFrame.BUTTON, minus, tip);
        button(mx, my, px + 22, oy, 18, 15, ">", PrideFrame.BUTTON, plus, tip);
        fontRenderer.drawStringWithShadow(label, px + 46, oy + 4, 0xFFFFFF);
        oy += 19;
    }

    /** a row (wrapping) of choice chips */
    private void chips(int mx, int my, String label, String[] opts, java.util.function.Supplier<String> get, Consumer<String> set, String tip) {
        fontRenderer.drawString("§7" + label, px, oy + 4, 0xFFFFFF);
        int lx = px + Math.max(56, fontRenderer.getStringWidth(label) + 8), x = lx;
        for (String o : opts) {
            int w = fontRenderer.getStringWidth(o) + 10;
            if (x + w > px + pw - 8) { x = lx; oy += 17; }
            boolean on = o.equals(get.get()), over = in(mx, my, x, oy, w, 15);
            Gui.drawRect(x, oy, x + w, oy + 15, on ? 0xFF6A3FA0 : over ? 0xFF3A2E52 : 0xFF241C33);
            if (on) Gui.drawRect(x, oy + 14, x + w, oy + 15, ACCENT[ClientState.S.tab]);
            fontRenderer.drawStringWithShadow(o, x + 5, oy + 4, on ? 0xFFFFFF : 0xC8C0D8);
            if (over) hint = tip;
            hits.add(new Object[]{ x, oy, w, 15, (Runnable) () -> set.accept(o), Boolean.TRUE });
            x += w + 3;
        }
        oy += 19;
    }

    private void slider(int mx, int my, String label, String key, int min, int max, String tip) {
        int v = Math.max(min, Math.min(max, ClientState.v(key)));
        int lw = Math.min(130, pw / 3), x = px + lw, w = pw - lw - 48;
        fontRenderer.drawStringWithShadow(label, px, oy + 3, 0xFFFFFF);
        boolean over = in(mx, my, x - 3, oy, w + 6, 14);
        Gui.drawRect(x, oy + 6, x + w, oy + 8, 0xFF3D2168);
        int kx = x + (int) ((long) (v - min) * w / Math.max(1, max - min));
        Gui.drawRect(x, oy + 6, kx, oy + 8, ACCENT[ClientState.S.tab]);
        Gui.drawRect(kx - 3, oy + 1, kx + 3, oy + 13, over || key.equals(dragging) ? 0xFFFFFFFF : 0xFFE0D8F0);
        fontRenderer.drawStringWithShadow(String.valueOf(v), x + w + 8, oy + 3, 0xF5A9B8);
        // - / + nudges
        if (over) hint = tip + "  §8(drag, or scroll the wheel over it)";
        final int fx = x, fw = w;
        hits.add(new Object[]{ x - 3, oy, w + 6, 14, (Runnable) () -> { dragging = key; dragX = fx; dragW = fw; dragMin = min; dragMax = max; dragTo(lastMx); }, Boolean.FALSE, key, min, max });
        oy += 17;
    }

    private void dragTo(int mx) {
        if (dragging == null) return;
        int v = dragMin + Math.round((float) (mx - dragX) * (dragMax - dragMin) / Math.max(1, dragW));
        ClientState.S.vals.put(dragging, Math.max(dragMin, Math.min(dragMax, v)));
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    private int lastMx, lastMy;

    // ------------------------------------------------------------------ input
    @Override
    protected void mouseClicked(int mx, int my, int b) throws IOException {
        lastMx = mx; lastMy = my;
        blockField.mouseClicked(mx, my, b); maskField.mouseClicked(mx, my, b); searchField.mouseClicked(mx, my, b);
        if (b == 1 && in(mx, my, searchField.x, searchField.y, searchField.width, 14)) searchField.setText("");
        // block grid
        if (in(mx, my, gridX, gridY, gridCols * 18, gridRows * 18)) {
            int i = gridScroll * gridCols + ((my - gridY) / 18) * gridCols + (mx - gridX) / 18;
            if (i >= 0 && i < shown.size()) {
                String id = (String) shown.get(i)[1];
                if (b == 1) { maskField.setText(id); ClientState.S.useMask = true; }
                else if (isShiftKeyDown() && !blockField.getText().isEmpty()) blockField.setText(blockField.getText() + "," + id);
                else blockField.setText(id);
                click();
            }
            return;
        }
        if (b != 0) return;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            if (in(mx, my, (Integer) h[0], (Integer) h[1], (Integer) h[2], (Integer) h[3])) {
                sync();
                if ((Boolean) h[5]) click();
                ((Runnable) h[4]).run();
                if (ClientState.S.block != null) ClientState.remember(blockField.getText().trim().split(",")[0].replaceAll("^\\d+%", ""));
                ClientState.save();
                if (ClientState.S.closeAfter && ClientState.S.tab != 8 && ClientState.S.tab != 1 && (Boolean) h[5] && mc.currentScreen == this && h.length < 7) mc.displayGuiScreen(parent);
                return;
            }
        }
    }

    private void click() { if (ClientState.S.sounds) mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.2F)); }

    @Override
    protected void mouseClickMove(int mx, int my, int b, long t) { lastMx = mx; lastMy = my; if (dragging != null) dragTo(mx); }

    @Override
    protected void mouseReleased(int mx, int my, int state) { if (dragging != null) { dragging = null; ClientState.save(); } }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth, my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int d = wheel > 0 ? -1 : 1;
        // over a slider: nudge it
        for (Object[] h : hits)
            if (h.length >= 9 && in(mx, my, (Integer) h[0], (Integer) h[1], (Integer) h[2], (Integer) h[3])) {
                String k = (String) h[6];
                ClientState.S.vals.put(k, Math.max((Integer) h[7], Math.min((Integer) h[8], ClientState.v(k) - d * (isShiftKeyDown() ? 5 : 1))));
                return;
            }
        if (in(mx, my, gridX, gridY, gridCols * 18, gridRows * 18)) {
            int rows = (shown.size() + gridCols - 1) / gridCols;
            gridScroll = Math.max(0, Math.min(Math.max(0, rows - gridRows), gridScroll + d * 2));
        } else if (in(mx, my, px - 4, pageTop, pw + 8, pageBottom - pageTop)) {
            scroll = Math.max(0, Math.min(Math.max(0, contentH - (pageBottom - pageTop) + 8), scroll + d * 24));
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (blockField.textboxKeyTyped(c, key) || maskField.textboxKeyTyped(c, key) || searchField.textboxKeyTyped(c, key)) return;
        if (key == Keyboard.KEY_ESCAPE || (key == StudioClient.OPEN.getKeyCode() && !blockField.isFocused())) { mc.displayGuiScreen(parent); return; }
        if (key == Keyboard.KEY_TAB) { ClientState.S.tab = (ClientState.S.tab + (isShiftKeyDown() ? TABS.length - 1 : 1)) % TABS.length; scroll = 0; return; }
        if (isCtrlKeyDown() && key == Keyboard.KEY_Z) { ClientState.send("undo", new NBTTagCompound()); return; }
        if (isCtrlKeyDown() && key == Keyboard.KEY_Y) { ClientState.send("redo", new NBTTagCompound()); return; }
        if (key >= Keyboard.KEY_1 && key <= Keyboard.KEY_9) { ClientState.S.tab = key - Keyboard.KEY_1; scroll = 0; }
    }

    @Override
    public void updateScreen() { blockField.updateCursorCounter(); maskField.updateCursorCounter(); searchField.updateCursorCounter(); }
}
