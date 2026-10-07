package com.dogpound.pridestudio.client;

import com.dogpound.pridestudio.edit.EditSession;
import com.dogpound.pridestudio.edit.Pattern;
import com.dogpound.pridestudio.edit.RegionOps;
import com.dogpound.pridestudio.edit.ShapeOps;
import com.dogpound.pridestudio.edit.WaterOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Live preview (Axiom's best trick, Requested): a tool is "armed" instead of run; a see-through ghost of every
 * block it would place follows your crosshair (or feet); Enter builds it, Backspace cancels. Same code as the real
 * tools, run as a dry run on the client.
 */
public final class Preview {
    private Preview() {}
    public static final String[] PREVIEWABLE = {"shape", "fill", "fillr", "set", "walls", "outline", "hollow", "line", "center", "overlay"};
    static String armed;                       // op waiting for Enter
    private static String lastKey = "";
    private static List<BlockPos> ghost = new ArrayList<BlockPos>();
    private static BlockPos ghostOrigin;
    private static boolean capped;

    public static boolean previewable(String op) { for (String p : PREVIEWABLE) if (p.equals(op)) return true; return false; }

    public static void arm(String op) {
        armed = op; lastKey = "";
        ClientState.say("✦ Preview: " + op + "  §7— Enter builds it, Backspace cancels, move to place it");
    }

    public static void cancel() { if (armed != null) ClientState.say("Preview cancelled"); armed = null; ghost.clear(); }

    public static void apply() {
        if (armed == null) return;
        NBTTagCompound t = new NBTTagCompound();
        if (ghostOrigin != null && !regionOp(armed)) ClientState.at(t, ghostOrigin);
        ClientState.run(armed, t, false);
        armed = null; ghost.clear();
    }

    static boolean regionOp(String op) { return !(op.equals("shape") || op.equals("fill") || op.equals("fillr")); }

    /** where the ghost sits right now */
    static BlockPos origin() {
        Minecraft mc = Minecraft.getMinecraft();
        if (ClientState.S.workAt.equals("look")) {
            RayTraceResult r = ClientState.look();
            return r == null ? null : r.getBlockPos().offset(r.sideHit);
        }
        return new BlockPos(mc.player);
    }

    /** recompute the ghost when anything that matters changed (called every few ticks) */
    static void update() {
        Minecraft mc = Minecraft.getMinecraft();
        if (armed == null || mc.world == null || mc.player == null) return;
        BlockPos o = origin();
        NBTTagCompound a = ClientState.args();
        String key = armed + a + o + ClientState.pos1 + ClientState.pos2;
        if (key.equals(lastKey)) return;
        lastKey = key;
        ghostOrigin = o;
        EditSession es = new EditSession(mc.world, "preview", true);
        try {
            Pattern pat = Pattern.parse(ClientState.S.block.isEmpty() ? "stone" : ClientState.S.block);
            BlockPos mn = null, mx = null;
            if (ClientState.pos1 != null && ClientState.pos2 != null) {
                BlockPos p1 = ClientState.pos1, p2 = ClientState.pos2;
                mn = new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
                mx = new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));
            }
            int r = ClientState.v("radius"), d = ClientState.v("depth"), h = ClientState.v("height"), th = ClientState.v("thick"), am = ClientState.v("amount");
            switch (armed) {
                case "shape": if (o != null) ShapeOps.run(es, ClientState.S.shape, o, pat, r, h, th, am, ClientState.S.hollow); break;
                case "fill": if (o != null) WaterOps.fill(es, o, pat, r, d, false); break;
                case "fillr": if (o != null) WaterOps.fill(es, o, pat, r, d, true); break;
                default:
                    if (mn == null) break;
                    switch (armed) {
                        case "set": RegionOps.set(es, mn, mx, pat, null); break;
                        case "walls": RegionOps.walls(es, mn, mx, pat, false); break;
                        case "outline": RegionOps.walls(es, mn, mx, pat, true); break;
                        case "hollow": RegionOps.hollow(es, mn, mx, th, null); break;
                        case "line": RegionOps.line(es, ClientState.pos1, ClientState.pos2, pat, th); break;
                        case "center": RegionOps.center(es, mn, mx, pat); break;
                        case "overlay": RegionOps.overlay(es, mn, mx, pat, d); break;
                        default: break;
                    }
            }
        } catch (Throwable ignored) { }
        ghost = new ArrayList<BlockPos>(es.positions());
        capped = ghost.size() >= EditSession.PREVIEW_LIMIT;
    }

    /** see-through pink boxes with white edges, one draw call */
    static void render(double cx, double cy, double cz) {
        if (armed == null || ghost.isEmpty()) return;
        long t = System.currentTimeMillis();
        float pulse = 0.18F + 0.08F * (float) Math.sin(t / 250.0);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder b = tes.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        int n = 0;
        for (BlockPos p : ghost) {
            if (n++ > 20000) break;              // keep it smooth; the build still does everything
            double x = p.getX() - cx, y = p.getY() - cy, z = p.getZ() - cz, e = 0.02;
            cube(b, x + e, y + e, z + e, x + 1 - e, y + 1 - e, z + 1 - e, 0.96F, 0.55F, 0.72F, pulse);
        }
        tes.draw();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
    }

    private static void cube(BufferBuilder b, double x0, double y0, double z0, double x1, double y1, double z1, float r, float g, float bl, float a) {
        double[][] f = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        for (double[] q : f) for (int i = 0; i < 12; i += 3) b.pos(q[i], q[i + 1], q[i + 2]).color(r, g, bl, a).endVertex();
    }

    public static String status() { return armed == null ? null : armed + ": " + ghost.size() + (capped ? "+" : "") + " blocks"; }
}
