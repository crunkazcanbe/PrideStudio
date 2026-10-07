package com.dogpound.pridestudio.client;

import com.dogpound.pridestudio.StudioItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Keys, the Esc-menu button, wand/brush clicks, and drawing the selection box + brush target in the world. */
public final class StudioClient {
    private StudioClient() {}
    static final String CAT = "Pride Studio";
    public static final KeyBinding OPEN = new KeyBinding("Open the Pride Studio panel", KeyConflictContext.IN_GAME, Keyboard.KEY_BACKSLASH, CAT);
    static final KeyBinding UNDO = new KeyBinding("Pride Studio: undo", KeyConflictContext.IN_GAME, KeyModifier.CONTROL, Keyboard.KEY_Z, CAT);
    static final KeyBinding REDO = new KeyBinding("Pride Studio: redo", KeyConflictContext.IN_GAME, KeyModifier.CONTROL, Keyboard.KEY_Y, CAT);
    static final KeyBinding POS1 = new KeyBinding("Pride Studio: corner 1 at crosshair", KeyConflictContext.IN_GAME, Keyboard.KEY_LBRACKET, CAT);
    static final KeyBinding POS2 = new KeyBinding("Pride Studio: corner 2 at crosshair", KeyConflictContext.IN_GAME, Keyboard.KEY_RBRACKET, CAT);
    static final KeyBinding COPY = new KeyBinding("Pride Studio: copy selection", KeyConflictContext.IN_GAME, KeyModifier.CONTROL, Keyboard.KEY_C, CAT);
    static final KeyBinding PASTE = new KeyBinding("Pride Studio: paste", KeyConflictContext.IN_GAME, KeyModifier.CONTROL, Keyboard.KEY_V, CAT);
    static final KeyBinding FILL = new KeyBinding("Pride Studio: quick fill here", KeyConflictContext.IN_GAME, Keyboard.KEY_NONE, CAT);
    static final KeyBinding BIGGER = new KeyBinding("Pride Studio: brush bigger", KeyConflictContext.IN_GAME, Keyboard.KEY_EQUALS, CAT);
    static final KeyBinding SMALLER = new KeyBinding("Pride Studio: brush smaller", KeyConflictContext.IN_GAME, Keyboard.KEY_MINUS, CAT);
    public static final KeyBinding MENU = new KeyBinding("Pride Studio: builder menu (hold)", KeyConflictContext.IN_GAME, Keyboard.KEY_LMENU, CAT);
    static final KeyBinding APPLY = new KeyBinding("Pride Studio: build the preview", KeyConflictContext.IN_GAME, Keyboard.KEY_RETURN, CAT);
    static final KeyBinding CANCEL = new KeyBinding("Pride Studio: cancel the preview", KeyConflictContext.IN_GAME, Keyboard.KEY_BACK, CAT);
    public static final KeyBinding EDITOR = new KeyBinding("Pride Studio: Editor Mode (fly out, brush the land)", KeyConflictContext.IN_GAME, Keyboard.KEY_GRAVE, CAT);
    static final int ESC_BUTTON = 0x5D5701;

    public static void register() {
        ClientState.load();
        for (KeyBinding k : new KeyBinding[]{ OPEN, EDITOR, MENU, APPLY, CANCEL, UNDO, REDO, POS1, POS2, COPY, PASTE, FILL, BIGGER, SMALLER }) ClientRegistry.registerKeyBinding(k);
        MinecraftForge.EVENT_BUS.register(new StudioClient.Events());
        Caps.register();
        EditorMode.register();
    }

    static boolean holding(Item i) {
        EntityPlayer p = Minecraft.getMinecraft().player;
        return p != null && p.getHeldItemMainhand().getItem() == i;
    }

    public static final class Events {
        @SubscribeEvent
        public void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null) return;
            while (OPEN.isPressed()) mc.displayGuiScreen(new StudioScreen(null));
            while (EDITOR.isPressed()) EditorMode.enter();
            while (MENU.isPressed()) if (mc.player.isCreative() || ClientState.S.menuSurvival) mc.displayGuiScreen(new BuilderMenu());
            while (UNDO.isPressed()) ClientState.send("undo", new NBTTagCompound());
            while (REDO.isPressed()) ClientState.send("redo", new NBTTagCompound());
            while (POS1.isPressed()) corner("pos1");
            while (POS2.isPressed()) corner("pos2");
            while (COPY.isPressed()) ClientState.send("copy", new NBTTagCompound());
            while (PASTE.isPressed()) ClientState.run("paste", null);
            while (FILL.isPressed()) ClientState.run("fill", null);
            while (APPLY.isPressed()) Preview.apply();
            while (CANCEL.isPressed()) Preview.cancel();
            if (mc.player.ticksExisted % 3 == 0) Preview.update();
            while (BIGGER.isPressed()) if (holding(StudioItems.BRUSH)) bump(1);
            while (SMALLER.isPressed()) if (holding(StudioItems.BRUSH)) bump(-1);
        }

        private void corner(String op) {
            RayTraceResult r = ClientState.look();
            NBTTagCompound t = new NBTTagCompound();
            if (r != null) ClientState.at(t, r.getBlockPos());
            ClientState.send(op, t);
        }

        private void bump(int d) {
            int s = Math.max(0, Math.min(32, ClientState.v("size") + d));
            ClientState.S.vals.put("size", s);
            ClientState.save();
            ClientState.say("Brush size " + s);
        }

        // ---------------------------------------------------------------- wand / brush left clicks (right clicks are in the item)
        @SubscribeEvent
        public void leftBlock(PlayerInteractEvent.LeftClickBlock e) {
            if (!(holding(StudioItems.WAND) || holding(StudioItems.BRUSH))) return;
            e.setCanceled(true);
            if (e.getWorld().isRemote) ClientState.toolUse(holding(StudioItems.WAND), true);
        }

        @SubscribeEvent
        public void leftAir(PlayerInteractEvent.LeftClickEmpty e) {
            if (holding(StudioItems.WAND) || holding(StudioItems.BRUSH)) ClientState.toolUse(holding(StudioItems.WAND), true);
        }

        // ---------------------------------------------------------------- Esc menu button (PrideCanvas picks it up as a tile too)
        @SubscribeEvent
        public void escInit(GuiScreenEvent.InitGuiEvent.Post e) {
            if (!(e.getGui() instanceof GuiIngameMenu)) return;
            int w = e.getGui().width, h = e.getGui().height;
            e.getButtonList().add(new GuiButton(ESC_BUTTON, w / 2 - 100, h / 4 + 152, "✦ Pride Studio"));
        }

        @SubscribeEvent
        public void escClick(GuiScreenEvent.ActionPerformedEvent.Post e) {
            if (e.getGui() instanceof GuiIngameMenu && e.getButton().id == ESC_BUTTON)
                Minecraft.getMinecraft().displayGuiScreen(new StudioScreen(null));
        }

        // ---------------------------------------------------------------- HUD line while holding a tool
        @SubscribeEvent
        public void hud(RenderGameOverlayEvent.Text e) {
            if (!ClientState.S.showHud) return;
            String pv = Preview.status();
            if (pv != null) e.getLeft().add("\u00A7d\u2726 Preview " + pv + " \u00A77(Enter = build, Backspace = cancel)");
            if (holding(StudioItems.BRUSH))
                e.getLeft().add("§d✦ Brush: §f" + ClientState.S.brush + " §7size §f" + ClientState.v("size") + " §7block §f" + ClientState.S.block + (ClientState.S.useMask ? " §7mask §f" + ClientState.S.mask : ""));
            else if (holding(StudioItems.WAND))
                e.getLeft().add("§d✦ Wand: §f" + (ClientState.pos1 == null ? "no corner 1" : "1 = " + str(ClientState.pos1)) + "  " + (ClientState.pos2 == null ? "no corner 2" : "2 = " + str(ClientState.pos2)));
        }

        static String str(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }

        // ---------------------------------------------------------------- world overlay
        @SubscribeEvent
        public void world(RenderWorldLastEvent e) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null) return;
            float pt = e.getPartialTicks();
            net.minecraft.entity.Entity v = mc.getRenderViewEntity() == null ? mc.player : mc.getRenderViewEntity();   // the editor camera too
            double cx = v.lastTickPosX + (v.posX - v.lastTickPosX) * pt;
            double cy = v.lastTickPosY + (v.posY - v.lastTickPosY) * pt;
            double cz = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * pt;
            GlStateManager.pushMatrix();
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.disableDepth();
            GL11.glLineWidth(2.5F);
            BlockPos a = ClientState.pos1, b = ClientState.pos2;
            if (ClientState.S.showBox && (a != null || b != null)) {
                int c = boxColor(mc);
                float r = (c >> 16 & 255) / 255F, g = (c >> 8 & 255) / 255F, bl = (c & 255) / 255F;
                if (a != null && b != null) {
                    AxisAlignedBB box = new AxisAlignedBB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                            Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1).offset(-cx, -cy, -cz).grow(0.005);
                    RenderGlobal.drawSelectionBoundingBox(box, r, g, bl, 0.9F);
                    RenderGlobal.renderFilledBox(box, r, g, bl, 0.06F);
                }
                if (a != null) RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(a).offset(-cx, -cy, -cz).grow(0.02), 1F, 0.55F, 0.75F, 1F);
                if (b != null) RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(b).offset(-cx, -cy, -cz).grow(0.02), 0.36F, 0.8F, 0.98F, 1F);
            }
            if (ClientState.S.showTarget && holding(StudioItems.BRUSH)) {
                RayTraceResult hit = ClientState.look();
                if (hit != null) {
                    int s = ClientState.v("size");
                    AxisAlignedBB t = new AxisAlignedBB(hit.getBlockPos()).offset(-cx, -cy, -cz);
                    RenderGlobal.drawSelectionBoundingBox(t.grow(s), 0.96F, 0.66F, 0.72F, 0.7F);
                    RenderGlobal.renderFilledBox(t.grow(0.01), 0.96F, 0.66F, 0.72F, 0.25F);
                }
            }
            Preview.render(cx, cy, cz);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }

        static int boxColor(Minecraft mc) {
            switch (ClientState.S.boxColor) {
                case "pink": return 0xF5A9B8;
                case "blue": return 0x5BCEFA;
                case "white": return 0xFFFFFF;
                case "green": return 0x8CE06A;
                case "gold": return 0xFFD54A;
                default: {
                    float hue = (System.currentTimeMillis() % 6000) / 6000F;
                    return java.awt.Color.HSBtoRGB(hue, 0.6F, 1F) & 0xFFFFFF;
                }
            }
        }
    }
}
