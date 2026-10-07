package com.dogpound.pridestudio.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/**
 * Editor Mode (Axiom-style): the camera leaves your body and orbits a pivot point like Blender
 * (right-drag orbit, Shift+right-drag pan, wheel zoom, WASD/Space/Shift fly the pivot), the mouse
 * is free, and brushes paint the land under the cursor from any distance. Your body stays where you
 * left it and you see it in the world. Esc or the editor key goes back.
 */
public final class EditorMode {
    private EditorMode() {}

    /** The camera: a client-only entity the game renders from. Never added to the world. */
    static final class Cam extends Entity {
        Cam(World w) { super(w); setSize(0.01f, 0.01f); noClip = true; }
        @Override protected void entityInit() {}
        @Override protected void readEntityFromNBT(NBTTagCompound c) {}
        @Override protected void writeEntityToNBT(NBTTagCompound c) {}
        @Override public float getEyeHeight() { return 0; }
        @Override public boolean isInvisible() { return true; }
    }

    static boolean active;
    static Cam cam;
    /** orbit: pivot, angles (Minecraft yaw/pitch, degrees), distance */
    static double px, py, pz, dist = 24;
    static float yaw, pitch = 40;
    private static boolean hideGuiBefore;
    private static int thirdBefore;
    /** what the mouse points at (updated every frame while the editor is open) */
    static RayTraceResult hover;
    /** the ruler tool's two points (drawn as a line with end boxes) */
    static BlockPos ruler1, ruler2;

    public static void register() { MinecraftForge.EVENT_BUS.register(new EditorMode.Events()); }

    public static boolean active() { return active; }

    public static void enter() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || active) return;
        if (!mc.player.isCreative() && !ClientState.S.menuSurvival) { ClientState.say("§cEditor Mode needs creative mode."); return; }
        RayTraceResult r = mc.player.rayTrace(160, 1F);
        Vec3d at = r != null && r.typeOfHit == RayTraceResult.Type.BLOCK ? new Vec3d(r.getBlockPos()).addVector(0.5, 0.5, 0.5) : mc.player.getPositionVector();
        px = at.x; py = at.y; pz = at.z;
        yaw = mc.player.rotationYaw;
        pitch = 45;
        dist = Math.max(12, Math.min(48, r != null ? r.hitVec.distanceTo(mc.player.getPositionEyes(1F)) + 8 : 24));
        cam = new Cam(mc.world);
        place();
        hideGuiBefore = mc.gameSettings.hideGUI;
        thirdBefore = mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView = 0;
        mc.setRenderViewEntity(cam);
        active = true;
        mc.displayGuiScreen(new EditorScreen());
    }

    public static void exit() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!active) return;
        active = false;
        hover = null;
        mc.gameSettings.hideGUI = hideGuiBefore;
        mc.gameSettings.thirdPersonView = thirdBefore;
        if (mc.player != null) mc.setRenderViewEntity(mc.player);
        cam = null;
        if (mc.currentScreen instanceof EditorScreen) mc.displayGuiScreen(null);
    }

    /** Camera position = pivot pulled back along the view direction. */
    static Vec3d eye() {
        Vec3d f = forward();
        return new Vec3d(px - f.x * dist, py - f.y * dist, pz - f.z * dist);
    }

    static Vec3d forward() {
        float y = yaw * 0.017453292F, p = pitch * 0.017453292F;
        return new Vec3d(-MathHelper.sin(y) * MathHelper.cos(p), -MathHelper.sin(p), MathHelper.cos(y) * MathHelper.cos(p));
    }

    static Vec3d right() {
        Vec3d f = forward();
        Vec3d r = new Vec3d(-f.z, 0, f.x);
        double l = r.lengthVector();
        return l < 1e-6 ? new Vec3d(1, 0, 0) : r.scale(1 / l);
    }

    static Vec3d up() {
        Vec3d f = forward(), r = right();
        return r.crossProduct(f).normalize();
    }

    /** Put the camera entity where the orbit says (no interpolation: it moves exactly with the mouse). */
    static void place() {
        if (cam == null) return;
        Vec3d e = eye();
        cam.posX = cam.prevPosX = cam.lastTickPosX = e.x;
        cam.posY = cam.prevPosY = cam.lastTickPosY = e.y;
        cam.posZ = cam.prevPosZ = cam.lastTickPosZ = e.z;
        cam.rotationYaw = cam.prevRotationYaw = yaw;
        cam.rotationPitch = cam.prevRotationPitch = pitch;
    }

    static void orbit(float dYaw, float dPitch) {
        yaw += dYaw;
        pitch = MathHelper.clamp(pitch + dPitch, -89.9F, 89.9F);
        place();
    }

    static void pan(double dx, double dy) {
        Vec3d r = right(), u = up();
        double k = dist * 0.0018;
        px += (-r.x * dx + u.x * dy) * k;
        py += (-r.y * dx + u.y * dy) * k;
        pz += (-r.z * dx + u.z * dy) * k;
        place();
    }

    static void zoom(int wheel) {
        dist = MathHelper.clamp(dist * (wheel > 0 ? 0.87 : 1.15), 2, 400);
        place();
    }

    /** Fly the pivot with the keyboard, along the ground plane (Space/Shift up and down). */
    static void fly(double fwd, double side, double vert, double speed) {
        Vec3d f = forward();
        double fl = Math.sqrt(f.x * f.x + f.z * f.z);
        double fx = fl < 1e-6 ? 0 : f.x / fl, fz = fl < 1e-6 ? 0 : f.z / fl;
        Vec3d r = right();
        double s = speed * Math.max(0.4, dist / 24);
        px += (fx * fwd + r.x * side) * s;
        pz += (fz * fwd + r.z * side) * s;
        py += vert * s;
        place();
    }

    /** The ray under the mouse (window pixels, y down), from the camera into the world. */
    static RayTraceResult pick(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || cam == null) return null;
        double nx = mouseX / (double) mc.displayWidth * 2 - 1, ny = 1 - mouseY / (double) mc.displayHeight * 2;
        double tanY = Math.tan(Math.toRadians(mc.gameSettings.fovSetting) / 2), tanX = tanY * mc.displayWidth / (double) mc.displayHeight;
        Vec3d f = forward(), r = right(), u = up();
        Vec3d dir = f.add(r.scale(nx * tanX)).add(u.scale(ny * tanY)).normalize();
        Vec3d from = eye(), to = from.add(dir.scale(600));
        return mc.world.rayTraceBlocks(from, to, false, true, false);
    }

    /** Fly the camera back to just behind your body. */
    static void home() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        px = mc.player.posX; py = mc.player.posY + 1; pz = mc.player.posZ;
        yaw = mc.player.rotationYaw; pitch = 30; dist = 8;
        place();
    }

    /** Move the pivot onto what the mouse points at (Blender "frame selected"). */
    static void focusHover() {
        if (hover == null || hover.typeOfHit != RayTraceResult.Type.BLOCK) return;
        Vec3d e = eye();
        BlockPos b = hover.getBlockPos();
        px = b.getX() + 0.5; py = b.getY() + 0.5; pz = b.getZ() + 0.5;
        dist = MathHelper.clamp(e.distanceTo(new Vec3d(px, py, pz)) * 0.7, 6, 200);
        place();
    }

    public static final class Events {
        @SubscribeEvent
        public void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !active) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null || mc.world == null || mc.player.isDead) { exit(); return; }
            if (cam != null && cam.world != mc.world) cam.world = mc.world;      // dimension change
            if (!(mc.currentScreen instanceof EditorScreen) && mc.currentScreen == null) exit();
        }

        @SubscribeEvent
        public void render(TickEvent.RenderTickEvent e) {
            if (e.phase == TickEvent.Phase.START && active) place();
        }

        /** no hotbar/crosshair while editing: our toolbar replaces them */
        @SubscribeEvent
        public void hud(RenderGameOverlayEvent.Pre e) {
            if (active) e.setCanceled(true);                                   // everything, incl. chat and other mods' HUDs
        }

        /** the brush ring on the ground + the block under the mouse */
        @SubscribeEvent
        public void ruler(RenderWorldLastEvent e) {
            if (!active || ruler1 == null) return;
            Vec3d c = eye();
            GlStateManager.disableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.glLineWidth(3f);
            RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(ruler1).offset(-c.x, -c.y, -c.z), 1f, 0.9f, 0.3f, 1f);
            if (ruler2 != null) {
                RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(ruler2).offset(-c.x, -c.y, -c.z), 1f, 0.9f, 0.3f, 1f);
                Tessellator t = Tessellator.getInstance();
                BufferBuilder b = t.getBuffer();
                b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
                b.pos(ruler1.getX() + 0.5 - c.x, ruler1.getY() + 0.5 - c.y, ruler1.getZ() + 0.5 - c.z).color(1f, 0.9f, 0.3f, 1f).endVertex();
                b.pos(ruler2.getX() + 0.5 - c.x, ruler2.getY() + 0.5 - c.y, ruler2.getZ() + 0.5 - c.z).color(1f, 0.9f, 0.3f, 1f).endVertex();
                t.draw();
            }
            GlStateManager.glLineWidth(1f);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
        }

        @SubscribeEvent
        public void world(RenderWorldLastEvent e) {
            if (!active || hover == null || hover.typeOfHit != RayTraceResult.Type.BLOCK) return;
            Minecraft mc = Minecraft.getMinecraft();
            Vec3d c = eye();
            BlockPos b = hover.getBlockPos();
            int r = EditorScreen.radius();
            GlStateManager.pushMatrix();
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.disableDepth();
            GlStateManager.glLineWidth(2.5f);
            float[] col = EditorScreen.toolColor();
            RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(b).grow(0.004).offset(-c.x, -c.y, -c.z), 1f, 1f, 1f, 0.9f);
            // ring that hugs the ground: each point drops to the surface below it
            Tessellator t = Tessellator.getInstance();
            BufferBuilder buf = t.getBuffer();
            buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
            int seg = Math.max(24, r * 8);
            for (int i = 0; i < seg; i++) {
                double a = i * Math.PI * 2 / seg;
                double x = b.getX() + 0.5 + Math.cos(a) * (r + 0.5), z = b.getZ() + 0.5 + Math.sin(a) * (r + 0.5);
                double y = ground(mc.world, x, b.getY() + 6, z, b.getY()) + 0.06;
                buf.pos(x - c.x, y - c.y, z - c.z).color(col[0], col[1], col[2], 0.95f).endVertex();
            }
            t.draw();
            // centre cross on the surface
            buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            double cy = b.getY() + 1.06;
            buf.pos(b.getX() + 0.5 - 0.6 - c.x, cy - c.y, b.getZ() + 0.5 - c.z).color(col[0], col[1], col[2], 1f).endVertex();
            buf.pos(b.getX() + 0.5 + 0.6 - c.x, cy - c.y, b.getZ() + 0.5 - c.z).color(col[0], col[1], col[2], 1f).endVertex();
            buf.pos(b.getX() + 0.5 - c.x, cy - c.y, b.getZ() + 0.5 - 0.6 - c.z).color(col[0], col[1], col[2], 1f).endVertex();
            buf.pos(b.getX() + 0.5 - c.x, cy - c.y, b.getZ() + 0.5 + 0.6 - c.z).color(col[0], col[1], col[2], 1f).endVertex();
            t.draw();
            GlStateManager.enableDepth();
            GlStateManager.glLineWidth(1f);
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }

        private static double ground(World w, double x, int fromY, double z, int fallback) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int y = fromY; y > fromY - 24 && y > 0; y--) {
                m.setPos(MathHelper.floor(x), y, MathHelper.floor(z));
                if (!w.isAirBlock(m) && w.getBlockState(m).getMaterial().blocksMovement()) return y + 1;
            }
            return fallback + 1;
        }
    }
}
