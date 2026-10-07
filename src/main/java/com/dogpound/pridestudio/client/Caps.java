package com.dogpound.pridestudio.client;

import com.dogpound.pridestudio.StudioItems;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Builder capabilities (the switches down the left of the builder menu). Ideas from Axiom's capabilities, written fresh:
 * every one is our own client logic + the creative/op-only Pride Studio ops on the server.
 */
public final class Caps {
    private Caps() {}

    public static final String[][] ALL = {
            {"bulldozer", "Bulldozer", "Hold left click to break blocks very fast"},
            {"reach", "Far reach", "Place and break blocks far away (up to the Reach setting)"},
            {"angel", "Angel place", "Place blocks in mid-air, 3 blocks in front of you"},
            {"replace", "Replace mode", "Right click swaps the block you look at for the one in your hand"},
            {"fastplace", "Fast place", "No delay between placing blocks"},
            {"tinker", "Pick exact", "Middle click copies the exact block (with its facing etc.) into Pride Studio's block"},
            {"bright", "Full bright", "See in the dark: no shadows"},
            {"noclipfly", "Steady flight", "Creative flight stops instantly when you let go of the keys"}};

    public static boolean on(String k) { return ClientState.S.caps.contains(k); }

    public static void toggle(String k) {
        if (!ClientState.S.caps.remove(k)) ClientState.S.caps.add(k);
        if (k.equals("bright") && !on("bright")) Minecraft.getMinecraft().gameSettings.gammaSetting = ClientState.S.savedGamma;
        if (k.equals("bright") && on("bright")) ClientState.S.savedGamma = Minecraft.getMinecraft().gameSettings.gammaSetting;
        ClientState.save();
    }

    public static void register() { MinecraftForge.EVENT_BUS.register(new Caps.Events()); }

    static boolean builder() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.player != null && mc.player.isCreative() && mc.currentScreen == null
                && mc.player.getHeldItemMainhand().getItem() != StudioItems.WAND && mc.player.getHeldItemMainhand().getItem() != StudioItems.BRUSH;
    }

    static RayTraceResult far() {
        Minecraft mc = Minecraft.getMinecraft();
        RayTraceResult r = mc.player.rayTrace(on("reach") ? ClientState.v("range") : 5, 1F);
        return r != null && r.typeOfHit == RayTraceResult.Type.BLOCK ? r : null;
    }

    static void send(String op, BlockPos p) {
        NBTTagCompound t = new NBTTagCompound();
        ClientState.at(t, p);
        ClientState.send(op, t);
    }

    public static final class Events {
        private int bulldozeTick;
        private boolean middleWas;
        private float applied = 1F;

        @SubscribeEvent
        public void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null) return;
            if (on("bright")) mc.gameSettings.gammaSetting = 100F;
            float speed = ClientState.v("flight") / 100F;
            if (mc.player.isCreative() && (speed != 1F || applied != 1F)) { mc.player.capabilities.setFlySpeed(0.05F * speed); applied = speed; }
            if (!builder()) { middleWas = false; return; }
            if (on("fastplace")) try { ObfuscationReflectionHelper.setPrivateValue(Minecraft.class, mc, 0, "rightClickDelayTimer", "field_71467_ac"); } catch (Throwable ignored) { }
            if (on("noclipfly") && mc.player.capabilities.isFlying && mc.player.movementInput.moveForward == 0 && mc.player.movementInput.moveStrafe == 0 && !mc.gameSettings.keyBindJump.isKeyDown() && !mc.gameSettings.keyBindSneak.isKeyDown()) {
                mc.player.motionX = 0; mc.player.motionZ = 0; mc.player.motionY = 0;
            }
            if (on("bulldozer") && mc.gameSettings.keyBindAttack.isKeyDown() && ++bulldozeTick % 2 == 0) {
                RayTraceResult r = far();
                if (r != null) { send("breakone", r.getBlockPos()); mc.world.setBlockToAir(r.getBlockPos()); }
            }
            boolean middle = mc.gameSettings.keyBindPickBlock.isKeyDown();
            if (on("tinker") && middle && !middleWas) {
                RayTraceResult r = far();
                if (r != null) { ClientState.S.block = StudioScreen.idOf(mc.world.getBlockState(r.getBlockPos())); ClientState.remember(ClientState.S.block); ClientState.save(); ClientState.say("Pride Studio block: " + ClientState.S.block); }
            }
            middleWas = middle;
        }

        /** replace mode + far reach on a block */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void rightBlock(PlayerInteractEvent.RightClickBlock e) {
            if (!e.getWorld().isRemote || !builder() || !(e.getItemStack().getItem() instanceof ItemBlock)) return;
            if (on("replace")) { e.setCanceled(true); send("replaceone", e.getPos()); }
        }

        /** right click with a block on air: far reach places on the far block, angel places in mid-air */
        @SubscribeEvent
        public void rightAir(PlayerInteractEvent.RightClickItem e) {
            if (!e.getWorld().isRemote || !builder() || !(e.getItemStack().getItem() instanceof ItemBlock)) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) return;   // vanilla handles normal reach
            RayTraceResult r = on("reach") ? far() : null;
            if (r != null) { send(on("replace") ? "replaceone" : "place", on("replace") ? r.getBlockPos() : r.getBlockPos().offset(r.sideHit)); return; }
            if (on("angel")) {
                Vec3d eye = mc.player.getPositionEyes(1F), look = mc.player.getLookVec();
                send("place", new BlockPos(eye.add(look.scale(3))));
            }
        }

        /** far reach breaking */
        @SubscribeEvent
        public void leftAir(PlayerInteractEvent.LeftClickEmpty e) {
            if (!builder() || !on("reach") || on("bulldozer")) return;
            RayTraceResult r = far();
            if (r != null) send("breakone", r.getBlockPos());
        }
    }
}
