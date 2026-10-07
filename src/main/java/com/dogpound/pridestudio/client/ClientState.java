package com.dogpound.pridestudio.client;

import com.dogpound.pridestudio.net.StudioNet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the panel remembers (config/pridestudio-editor.json) and what the server last told us. Client only. */
public final class ClientState {
    private ClientState() {}

    public static final class Settings {
        public int tab = 0;
        public String block = "stone", mask = "", biome = "plains", dir = "look", shape = "sphere", brush = "ball", tree = "oak", workAt = "feet", boxColor = "rainbow";
        public boolean useMask = false, hollow = false, skipAir = true, moveSel = true, butcherAll = false;
        public boolean showBox = true, showTarget = true, showHud = true, closeAfter = false, sounds = true;
        public Map<String, Integer> vals = new LinkedHashMap<>();
        /** Editor Mode text options (shape, falloff, mode, biome…) */
        public Map<String, String> opts = new LinkedHashMap<>();
        public List<String> recent = new ArrayList<>();
        public List<String> caps = new ArrayList<>();
        public float savedGamma = 1F, menuScale = 1F;   // she liked the menu at its natural size (2026-10-04); 0 = automatic
        public boolean menuToggle = false, menuSurvival = false, preview = true;
        public int val(String k, int def) { Integer v = vals.get(k); return v == null ? def : v; }
    }

    public static Settings S = new Settings();
    private static final File FILE = new File("config/pridestudio-editor.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // what the server told us last
    public static String msg = "";
    public static long msgAt;
    public static List<String> history = new ArrayList<>();
    public static int redo, clip;
    public static BlockPos pos1, pos2;
    private static long lastToolUse;

    public static void load() {
        if (FILE.isFile()) try (Reader r = new FileReader(FILE)) { Settings s = GSON.fromJson(r, Settings.class); if (s != null) S = s; } catch (Throwable ignored) { }
    }

    public static void save() { try (Writer w = new FileWriter(FILE)) { GSON.toJson(S, w); } catch (Throwable ignored) { } }

    public static final String[] VALS = {"radius", "depth", "size", "height", "amount", "thick", "density", "strength", "range", "flight"};
    public static final int[] DEFAULTS = {8, 4, 5, 8, 1, 1, 25, 2, 200, 100};
    public static int v(String k) { for (int i = 0; i < VALS.length; i++) if (VALS[i].equals(k)) return S.val(k, DEFAULTS[i]); return S.val(k, 1); }

    /** the args every tool gets: all sliders, block, mask, flags */
    public static NBTTagCompound args() {
        NBTTagCompound t = new NBTTagCompound();
        for (String k : VALS) t.setInteger(k, v(k));
        t.setString("block", S.block); t.setString("mask", S.mask); t.setBoolean("useMask", S.useMask);
        t.setString("dir", S.dir); t.setString("shape", S.shape); t.setString("brush", S.brush); t.setString("tree", S.tree); t.setString("biome", S.biome);
        t.setBoolean("hollow", S.hollow); t.setBoolean("skipAir", S.skipAir); t.setBoolean("moveSel", S.moveSel); t.setBoolean("all", S.butcherAll);
        return t;
    }

    /** what the crosshair points at, up to the reach setting (null if sky) */
    public static RayTraceResult look() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return null;
        RayTraceResult r = mc.player.rayTrace(v("range"), 1F);
        return r != null && r.typeOfHit == RayTraceResult.Type.BLOCK ? r : null;
    }

    public static void at(NBTTagCompound t, BlockPos p) { t.setInteger("x", p.getX()); t.setInteger("y", p.getY()); t.setInteger("z", p.getZ()); }

    /** run a tool where the settings say (your feet, or the empty block in front of what you look at) */
    public static void run(String op, NBTTagCompound extra) { run(op, extra, S.workAt.equals("look")); }

    public static void run(String op, NBTTagCompound extra, boolean atLook) {
        NBTTagCompound t = args();
        if (extra != null) t.merge(extra);
        if (atLook && !t.hasKey("x")) {
            RayTraceResult r = look();
            if (r == null) { say("§cNothing in reach where you're looking."); return; }
            at(t, r.getBlockPos().offset(r.sideHit));
        }
        send(op, t);
    }

    public static void send(String op, NBTTagCompound t) {
        if (Minecraft.getMinecraft().getConnection() == null) return;
        StudioNet.NET.sendToServer(new StudioNet.Op(op, t));
    }

    public static void remember(String block) {
        if (block == null || block.isEmpty()) return;
        S.recent.remove(block);
        S.recent.add(0, block);
        while (S.recent.size() > 12) S.recent.remove(S.recent.size() - 1);
    }

    /** Wand: left = corner 1, right = corner 2.  Brush: right = paint, left = reverse. Works at any distance up to the reach. */
    public static void toolUse(boolean wand, boolean left) {
        long now = System.currentTimeMillis();
        if (now - lastToolUse < 180) return;
        lastToolUse = now;
        RayTraceResult r = look();
        if (r == null) { say("§cNothing in reach where you're looking."); return; }
        NBTTagCompound t = args();
        at(t, r.getBlockPos());
        if (wand) { send(left ? "pos1" : "pos2", t); return; }
        t.setInteger("face", r.sideHit.getIndex());
        t.setBoolean("alt", left);
        remember(S.block);
        send("brush", t);
    }

    public static void reply(String text, NBTTagCompound st) {
        if (text.startsWith("#open:")) {
            Minecraft m = Minecraft.getMinecraft();
            if (text.endsWith("editor")) EditorMode.enter();
            else m.displayGuiScreen(text.endsWith("menu") ? new BuilderMenu(true) : new StudioScreen(null));
            text = "";
        }
        if (!text.isEmpty()) { msg = text; msgAt = System.currentTimeMillis(); }
        history.clear();
        NBTTagList h = st.getTagList("history", 8);
        for (int i = 0; i < h.tagCount(); i++) history.add(h.getStringTagAt(i));
        redo = st.getInteger("redo"); clip = st.getInteger("clip");
        int[] a = st.getIntArray("pos1"), b = st.getIntArray("pos2");
        pos1 = a.length == 3 ? new BlockPos(a[0], a[1], a[2]) : null;
        pos2 = b.length == 3 ? new BlockPos(b[0], b[1], b[2]) : null;
        Minecraft mc = Minecraft.getMinecraft();
        if (!text.isEmpty() && !(mc.currentScreen instanceof StudioScreen)) say(text);
    }

    public static void say(String s) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) mc.player.sendStatusMessage(new TextComponentString(s.startsWith("§") ? s : "§d" + s), true);
        msg = s; msgAt = System.currentTimeMillis();
    }
}
