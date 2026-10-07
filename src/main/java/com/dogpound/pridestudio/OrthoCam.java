package com.dogpound.pridestudio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Pride Camera — orthographic view (requested feature). Ideas and options from
 * OrthoCamera by DimasKama (MIT, https://github.com/DimasKama/OrthoCamera), written fresh for 1.12.2:
 * toggle ortho view, zoom, near/far planes, a FIXED camera angle you can still play under (isometric style), rotate the
 * fixed camera with keys, remember the on/off state. The projection swap is MixinOrthoCamera (EntityRenderer).
 */
public final class OrthoCam {
    private OrthoCam() {}

    public static final class Settings {
        public boolean enabled = false, rememberEnabled = false, hideHand = true;
        public float zoom = 24F;                   // half the screen height, in blocks
        public float near = -1000F, far = 1000F;
        public boolean fixed = false;
        public float yaw = 45F, pitch = 30F;       // fixed angle (classic isometric-ish)
        public float rotateSpeed = 3F;             // degrees per tick while a rotate key is held
    }

    public static Settings S = new Settings();
    private static float prevZoom = 24F, prevYaw = 45F, prevPitch = 30F;
    private static final File FILE = new File("config/pridestudio-camera.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final KeyBinding TOGGLE = new KeyBinding("Pride Studio camera: orthographic on/off", Keyboard.KEY_NUMPAD4, "Pride Studio");
    static final KeyBinding ZOOM_IN = new KeyBinding("Pride Studio camera: zoom in", Keyboard.KEY_ADD, "Pride Studio");
    static final KeyBinding ZOOM_OUT = new KeyBinding("Pride Studio camera: zoom out", Keyboard.KEY_SUBTRACT, "Pride Studio");
    static final KeyBinding FIX = new KeyBinding("Pride Studio camera: fix the angle", Keyboard.KEY_MULTIPLY, "Pride Studio");
    static final KeyBinding LEFT = new KeyBinding("Pride Studio camera: turn fixed view left", Keyboard.KEY_NUMPAD7, "Pride Studio");
    static final KeyBinding RIGHT = new KeyBinding("Pride Studio camera: turn fixed view right", Keyboard.KEY_NUMPAD9, "Pride Studio");
    static final KeyBinding UP = new KeyBinding("Pride Studio camera: tilt fixed view up", Keyboard.KEY_NUMPAD8, "Pride Studio");
    static final KeyBinding DOWN = new KeyBinding("Pride Studio camera: tilt fixed view down", Keyboard.KEY_NUMPAD2, "Pride Studio");
    static final KeyBinding OPTIONS = new KeyBinding("Pride Studio camera: settings", Keyboard.KEY_DIVIDE, "Pride Studio");

    public static void register() {
        load();
        if (!S.rememberEnabled) S.enabled = false;
        for (KeyBinding k : new KeyBinding[]{ TOGGLE, ZOOM_IN, ZOOM_OUT, FIX, LEFT, RIGHT, UP, DOWN, OPTIONS }) ClientRegistry.registerKeyBinding(k);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new OrthoCam.Events());
        prevZoom = S.zoom; prevYaw = S.yaw; prevPitch = S.pitch;
    }

    public static boolean on() { return S.enabled && Minecraft.getMinecraft().world != null; }
    public static float zoom(float pt) { return prevZoom + (S.zoom - prevZoom) * pt; }

    private static void say(String s) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) mc.player.sendStatusMessage(new TextComponentString("§d✦ " + s), true);
    }

    static void load() {
        if (FILE.isFile()) try (Reader r = new FileReader(FILE)) { Settings s = GSON.fromJson(r, Settings.class); if (s != null) S = s; } catch (Throwable ignored) { }
    }

    static void save() { try (Writer w = new FileWriter(FILE)) { GSON.toJson(S, w); } catch (Throwable ignored) { } }

    public static final class Events {
        @SubscribeEvent
        public void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            prevZoom = S.zoom; prevYaw = S.yaw; prevPitch = S.pitch;
            Minecraft mc = Minecraft.getMinecraft();
            boolean changed = false;
            while (TOGGLE.isPressed()) { S.enabled = !S.enabled; say(S.enabled ? "Orthographic view ON" : "Orthographic view off"); changed = true; }
            while (ZOOM_IN.isPressed()) { S.zoom = Math.max(1F, S.zoom / 1.1F); say(String.format("Zoom %.1f", S.zoom)); changed = true; }
            while (ZOOM_OUT.isPressed()) { S.zoom = Math.min(2000F, S.zoom * 1.1F); say(String.format("Zoom %.1f", S.zoom)); changed = true; }
            while (FIX.isPressed()) {
                S.fixed = !S.fixed;
                if (S.fixed && mc.player != null && S.pitch == 30F && S.yaw == 45F) { /* keep the isometric default */ }
                say(S.fixed ? "Camera angle fixed" : "Camera follows the mouse");
                changed = true;
            }
            if (S.fixed) {
                if (LEFT.isKeyDown()) S.yaw -= S.rotateSpeed;
                if (RIGHT.isKeyDown()) S.yaw += S.rotateSpeed;
                if (UP.isKeyDown()) S.pitch = Math.max(-90F, S.pitch - S.rotateSpeed);
                if (DOWN.isKeyDown()) S.pitch = Math.min(90F, S.pitch + S.rotateSpeed);
            }
            while (OPTIONS.isPressed()) mc.displayGuiScreen(new Screen(mc.currentScreen));
            if (changed) save();
        }

        /** the fixed angle: the player still turns with the mouse, only the camera keeps its angle */
        @SubscribeEvent
        public void camera(EntityViewRenderEvent.CameraSetup e) {
            if (!on() || !S.fixed) return;
            float pt = (float) e.getRenderPartialTicks();
            float yaw = prevYaw + (S.yaw - prevYaw) * pt, pitch = prevPitch + (S.pitch - prevPitch) * pt;
            e.setYaw(yaw + 180F);
            e.setPitch(pitch);
            e.setRoll(0F);
        }
    }

    // ------------------------------------------------------------------ settings screen (Pride style)

    public static final class Screen extends GuiScreen {
        private final GuiScreen parent;
        private final List<Object[]> hits = new ArrayList<>();
        private int oy;
        public Screen(GuiScreen parent) { this.parent = parent; }
        @Override public boolean doesGuiPauseGame() { return false; }

        @Override
        public void drawScreen(int mx, int my, float pt) {
            hits.clear();
            PrideFrame f = PrideFrame.sized(width, height, 460, 360);
            f.draw(this, "Pride Studio \u2014 Camera", "§7orthographic view");
            int x = f.cx + 4, w = f.cw - 8;
            oy = f.cy + 2;
            toggle(mx, my, x, w, "Orthographic view", S.enabled, () -> S.enabled = !S.enabled);
            toggle(mx, my, x, w, "Remember on/off after restarting", S.rememberEnabled, () -> S.rememberEnabled = !S.rememberEnabled);
            toggle(mx, my, x, w, "Hide your hand while on", S.hideHand, () -> S.hideHand = !S.hideHand);
            stepper(mx, my, x, w, String.format("Zoom (blocks to the top edge)  %.1f", S.zoom), () -> S.zoom = Math.max(1F, S.zoom / 1.25F), () -> S.zoom = Math.min(2000F, S.zoom * 1.25F));
            stepper(mx, my, x, w, String.format("Near plane  %.0f", S.near), () -> S.near = Math.max(-5000F, S.near - 100F), () -> S.near = Math.min(0F, S.near + 100F));
            stepper(mx, my, x, w, String.format("Far plane  %.0f", S.far), () -> S.far = Math.max(100F, S.far - 100F), () -> S.far = Math.min(10000F, S.far + 100F));
            toggle(mx, my, x, w, "Fixed camera angle (play under a still camera)", S.fixed, () -> S.fixed = !S.fixed);
            stepper(mx, my, x, w, String.format("Fixed turn  %.0f°", S.yaw), () -> S.yaw -= 15F, () -> S.yaw += 15F);
            stepper(mx, my, x, w, String.format("Fixed tilt  %.0f°", S.pitch), () -> S.pitch = Math.max(-90F, S.pitch - 5F), () -> S.pitch = Math.min(90F, S.pitch + 5F));
            stepper(mx, my, x, w, String.format("Turn speed  %.1f° per tick", S.rotateSpeed), () -> S.rotateSpeed = Math.max(0.5F, S.rotateSpeed - 0.5F), () -> S.rotateSpeed = Math.min(15F, S.rotateSpeed + 0.5F));
            int by = f.y + f.h - 24;
            fontRenderer.drawStringWithShadow("§7Keys: Numpad 4 on/off, + / - zoom, * fix angle, 7 9 8 2 turn, / this screen", f.cx, by - 12, 0xFFFFFF);
            PrideFrame.button(f.cx + f.cw - 80, by, 80, 18, "✔ Done", PrideFrame.BUTTON, mx, my);
            hits.add(new Object[]{ f.cx + f.cw - 80, by, 80, 18, (Runnable) () -> { save(); mc.displayGuiScreen(parent); } });
            super.drawScreen(mx, my, pt);
        }

        private void toggle(int mx, int my, int x, int w, String label, boolean v, Runnable r) {
            if (mx >= x && my >= oy && mx < x + w && my < oy + 16) Gui.drawRect(x - 2, oy - 1, x + w, oy + 15, 0x30FFFFFF);
            Gui.drawRect(x, oy + 4, x + 16, oy + 12, v ? 0xFF8CE06A : 0xFF3D2168);
            Gui.drawRect(v ? x + 9 : x + 1, oy + 5, v ? x + 15 : x + 7, oy + 11, 0xFFFFFFFF);
            fontRenderer.drawStringWithShadow(label, x + 22, oy + 4, v ? 0xFFFFFF : 0xA79FBF);
            hits.add(new Object[]{ x, oy, w, 16, r });
            oy += 18;
        }

        private void stepper(int mx, int my, int x, int w, String label, Runnable minus, Runnable plus) {
            PrideFrame.button(x, oy, 18, 15, "-", PrideFrame.BUTTON, mx, my);
            hits.add(new Object[]{ x, oy, 18, 15, minus });
            PrideFrame.button(x + 22, oy, 18, 15, "+", PrideFrame.BUTTON, mx, my);
            hits.add(new Object[]{ x + 22, oy, 18, 15, plus });
            fontRenderer.drawStringWithShadow(label, x + 46, oy + 4, 0xFFFFFF);
            oy += 18;
        }

        @Override
        protected void mouseClicked(int mx, int my, int b) throws IOException {
            if (b != 0) return;
            for (Object[] h : hits)
                if (mx >= (Integer) h[0] && my >= (Integer) h[1] && mx < (Integer) h[0] + (Integer) h[2] && my < (Integer) h[1] + (Integer) h[3]) {
                    ((Runnable) h[4]).run();
                    save();
                    return;
                }
        }

        @Override
        protected void keyTyped(char c, int key) throws IOException { if (key == Keyboard.KEY_ESCAPE) { save(); mc.displayGuiScreen(parent); } }
    }
}
