package com.dogpound.pridestudio;

import com.dogpound.pridestudio.client.ClientState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Pride Studio hand tools: hold one and click the world. Every tool is its own Blockbench model in the Pride
 * colours (requested feature). Sizes, blocks and masks come from the Pride Studio panel / Editor Mode.
 * Right click = the main action, left click = the second one (shown in the tooltip). Sneak + right click on most
 * tools opens the Pride Studio panel. Works at long range (the panel's reach setting).
 */
@Mod.EventBusSubscriber(modid = PrideStudio.MODID)
public final class HandTools {
    private HandTools() {}

    public static final CreativeTabs TAB = new CreativeTabs("pridestudio") {
        @Override public ItemStack getTabIconItem() { return new ItemStack(ALL.isEmpty() ? StudioItems.WAND : ALL.get(0)); }
    };

    /** id, name, right-click action, left-click action, what it does */
    static final String[][] DEFS = {
            // selection
            {"select_wand", "Selection Wand", "pos2", "pos1", "Left: corner 1 · Right: corner 2"},
            {"magic_wand", "Magic Wand", "magicselect", "selclear", "Right: select everything connected like this block · Left: clear the selection"},
            {"chunk_wand", "Chunk Wand", "selchunk", "fullheight", "Right: select this chunk · Left: stretch the selection bedrock to sky"},
            // clipboard
            {"copy_tool", "Copy Tool", "copy", "copy", "Copy the selection (where you stand is the handle)"},
            {"cut_shears", "Cut Shears", "cut", "cut", "Cut the selection into the clipboard"},
            {"paste_stamp", "Paste Stamp", "paste", "pasteair", "Right: paste here (skips air) · Left: paste with air"},
            {"rotate_tool", "Rotate Tool", "rotate90", "rotate-90", "Turn the clipboard: right 90° · left -90°"},
            {"flip_mirror", "Flip Mirror", "flip", "flipy", "Right: flip the clipboard the way you look · Left: upside down"},
            // move
            {"move_arrows", "Move Arrows", "move", "move10", "Move the selection the way you look: right 1 block · left 10"},
            {"stack_tool", "Stack Tool", "stack", "stack5", "Repeat the selection the way you look: right ×1 · left ×5"},
            {"nudge_tool", "Selection Nudger", "shift", "shiftback", "Move the selection box (not blocks): right forward · left back"},
            // shapes
            {"sphere_tool", "Sphere Tool", "shape:sphere", "shape:sphere:hollow", "Right: a ball · Left: a hollow ball (size from the panel)"},
            {"dome_tool", "Dome Tool", "shape:dome", "shape:dome:hollow", "Right: a dome · Left: hollow dome"},
            {"cylinder_tool", "Cylinder Tool", "shape:cylinder", "shape:cylinder:hollow", "Right: a cylinder · Left: hollow (tube)"},
            {"cone_tool", "Cone Tool", "shape:cone", "shape:cone:hollow", "Right: a cone · Left: hollow cone"},
            {"pyramid_tool", "Pyramid Tool", "shape:pyramid", "shape:pyramid:hollow", "Right: a pyramid · Left: hollow pyramid"},
            {"torus_tool", "Torus Tool", "shape:torus", "shape:torus:hollow", "Right: a ring / donut · Left: hollow"},
            {"cube_tool", "Cube Tool", "shape:cube", "shape:cube:hollow", "Right: a cube · Left: hollow cube"},
            {"line_tool", "Line Tool", "line", "line", "A line from corner 1 to corner 2"},
            // region
            {"fill_bucket", "Fill Bucket", "set", "fill", "Right: fill the selection with your block · Left: //fill a hole here"},
            {"replace_tool", "Replace Tool", "replace", "replacenear", "Right: replace the mask in the selection · Left: around you"},
            {"walls_tool", "Walls Tool", "walls", "outline", "Right: walls round the selection · Left: outline (walls+floor+roof)"},
            {"hollow_tool", "Hollow Tool", "hollow", "center", "Right: hollow the selection out · Left: mark its centre"},
            {"drain_sponge", "Drain Sponge", "drainsel", "drain", "Right: drain the selection · Left: drain around here"},
            {"nature_tool", "Naturalizer", "naturalize", "naturalizenear", "Grass on top, dirt, then stone: right selection · left around here"},
            // terrain brushes
            {"raise_trowel", "Raise Trowel", "b:raise", "b:lower", "Right: raise the ground · Left: lower it"},
            {"smooth_iron", "Smoothing Iron", "b:gsmooth", "b:melt", "Right: smooth · Left: melt sharp bits"},
            {"paint_brush", "Paint Brush", "b:painter", "b:noisepaint", "Right: paint your block · Left: paint a noise mix of your recent blocks"},
            {"erode_chisel", "Erode Chisel", "b:erode", "b:fillin", "Right: weather away edges · Left: fill nooks"},
            {"rock_hammer", "Rock Hammer", "b:rock", "b:shatter", "Right: build a boulder · Left: crack rock apart"},
            {"tree_planter", "Tree Planter", "b:tree", "forest", "Right: one tree · Left: a little forest"},
            {"flood_bucket", "Flood Bucket", "b:floodfill:water", "b:pour:water", "Right: flood fill with water · Left: pour a pond"},
            // utility
            {"ruler", "Ruler", "ruler2", "ruler1", "Left: first point · Right: second point → distance, size, slope"},
            {"undo_wand", "Undo Wand", "undo", "redo", "Right: undo · Left: redo"},
            {"toolbox_pink", "Pink Toolbox", "panel", "editor", "Right: the Pride Studio panel · Left: Editor Mode"},
            {"toolbox_blue", "Blue Toolbox", "menu", "editor", "Right: the builder menu · Left: Editor Mode"},
            {"guide_book", "Pride Studio Guide", "guide", "guide", "How every tool works"},
    };

    public static final List<HandTool> ALL = new ArrayList<>();

    public static final class HandTool extends Item {
        public final String id, name, right, left, help;

        HandTool(String[] d) {
            id = d[0]; name = d[1]; right = d[2]; left = d[3]; help = d[4];
            setRegistryName(PrideStudio.MODID, id);
            setUnlocalizedName(PrideStudio.MODID + "." + id);
            setMaxStackSize(1);
            setCreativeTab(TAB);
            setFull3D();
        }

        @Override public boolean canDestroyBlockInCreative(World w, BlockPos pos, ItemStack s, EntityPlayer p) { return false; }
        @Override public boolean onBlockStartBreak(ItemStack s, BlockPos pos, EntityPlayer p) { return true; }
        @Override public float getDestroySpeed(ItemStack s, IBlockState st) { return 0F; }

        @Override
        public EnumActionResult onItemUse(EntityPlayer p, World w, BlockPos pos, EnumHand hand, EnumFacing face, float hx, float hy, float hz) {
            if (w.isRemote) Client.use(this, false, p.isSneaking());
            return EnumActionResult.SUCCESS;
        }

        @Override
        public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
            if (w.isRemote) Client.use(this, false, p.isSneaking());
            return new ActionResult<>(EnumActionResult.SUCCESS, p.getHeldItem(hand));
        }

        @SideOnly(Side.CLIENT)
        @Override
        public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
            for (String part : help.split(" · ")) tip.add("§d" + part);
            tip.add("§7Size, block and mask: Pride Studio panel (\\) · Sneak + right click opens it");
        }

        @Override public String getItemStackDisplayName(ItemStack s) { return "§d" + name; }
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) {
        for (String[] d : DEFS) { HandTool t = new HandTool(d); ALL.add(t); e.getRegistry().register(t); }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        for (HandTool t : ALL) ModelLoader.setCustomModelResourceLocation(t, 0, new ModelResourceLocation(t.getRegistryName(), "inventory"));
    }

    // ---------------------------------------------------------------- left clicks

    @SubscribeEvent
    public static void leftBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getItemStack().getItem() instanceof HandTool)) return;
        e.setCanceled(true);
        if (e.getWorld().isRemote) Client.use((HandTool) e.getItemStack().getItem(), true, false);
    }

    @SubscribeEvent
    public static void leftAir(PlayerInteractEvent.LeftClickEmpty e) {
        if (e.getItemStack().getItem() instanceof HandTool) Client.use((HandTool) e.getItemStack().getItem(), true, false);
    }

    // ---------------------------------------------------------------- what each click does (client: sends Pride Studio ops)

    @SideOnly(Side.CLIENT)
    public static final class Client {
        private static long last;
        public static BlockPos ruler1, ruler2;

        static void use(HandTool t, boolean left, boolean sneak) {
            long now = System.currentTimeMillis();
            if (now - last < 200) return;
            last = now;
            Minecraft mc = Minecraft.getMinecraft();
            if (sneak && !left && !t.id.startsWith("toolbox") && !t.id.equals("guide_book")) { mc.displayGuiScreen(new com.dogpound.pridestudio.client.StudioScreen(null)); return; }
            String a = left ? t.left : t.right;
            RayTraceResult r = ClientState.look();
            BlockPos hit = r == null ? null : r.getBlockPos();
            BlockPos out = r == null ? null : r.getBlockPos().offset(r.sideHit);
            NBTTagCompound n = ClientState.args();
            switch (a) {
                case "pos1": case "pos2": case "magicselect": case "selchunk": case "replacenear": case "naturalizenear": case "drain":
                    if (hit == null) { ClientState.say("§cPoint at a block."); return; }
                    ClientState.at(n, a.equals("drain") || a.endsWith("near") ? out : hit);
                    if (a.equals("magicselect")) n.setInteger("limit", 100000);
                    ClientState.send(a, n);
                    return;
                case "paste": case "pasteair":
                    if (out != null) ClientState.at(n, out);
                    n.setBoolean("skipAir", a.equals("paste"));
                    ClientState.send("paste", n);
                    return;
                case "rotate90": n.setInteger("deg", 90); ClientState.send("rotate", n); return;
                case "rotate-90": n.setInteger("deg", 270); ClientState.send("rotate", n); return;
                case "flipy": n.setString("axis", "y"); ClientState.send("flip", n); return;
                case "move": case "move10": n.setString("dir", "look"); n.setInteger("amount", a.equals("move10") ? 10 : 1); ClientState.send("move", n); return;
                case "stack": case "stack5": n.setString("dir", "look"); n.setInteger("amount", a.equals("stack5") ? 5 : 1); ClientState.send("stack", n); return;
                case "shift": case "shiftback": n.setString("dir", a.equals("shift") ? "look" : "back"); n.setInteger("amount", 1); ClientState.send("shift", n); return;
                case "fill": case "forest": ClientState.run(a, null, true); return;
                case "ruler1": case "ruler2": ruler(a.equals("ruler1"), hit); return;
                case "panel": mc.displayGuiScreen(new com.dogpound.pridestudio.client.StudioScreen(null)); return;
                case "menu": mc.displayGuiScreen(new com.dogpound.pridestudio.client.BuilderMenu()); return;
                case "editor": com.dogpound.pridestudio.client.EditorMode.enter(); return;
                case "guide": guide(); return;
                default:
            }
            if (a.startsWith("shape:")) {
                if (out == null) { ClientState.say("§cPoint at a block."); return; }
                String[] s = a.split(":");
                ClientState.at(n, out);
                n.setString("shape", s[1]);
                n.setBoolean("hollow", s.length > 2);
                ClientState.send("shape", n);
                return;
            }
            if (a.startsWith("b:")) {
                if (hit == null) { ClientState.say("§cPoint at a block."); return; }
                String[] s = a.split(":");
                ClientState.at(n, hit);
                n.setString("brush", s[1]);
                n.setInteger("face", r.sideHit.getIndex());
                n.setInteger("size", ClientState.v("size"));
                if (s.length > 2) n.setString("block", s[2]);
                if (s[1].equals("noisepaint")) n.setString("blocks", String.join(",", ClientState.S.recent.subList(0, Math.min(3, ClientState.S.recent.size()))));
                ClientState.send("brush", n);
                return;
            }
            ClientState.send(a, n);                                    // copy, cut, flip, set, replace, walls, outline, hollow, center, drainsel, naturalize, line, undo, redo, fullheight, selclear
        }

        private static void ruler(boolean first, BlockPos p) {
            if (p == null) { ClientState.say("§cPoint at a block."); return; }
            if (first) { ruler1 = p; ruler2 = null; ClientState.say("📏 Point 1 at " + p.getX() + ", " + p.getY() + ", " + p.getZ()); return; }
            ruler2 = p;
            if (ruler1 == null) { ClientState.say("📏 Left click the first point"); return; }
            int dx = Math.abs(ruler2.getX() - ruler1.getX()), dy = Math.abs(ruler2.getY() - ruler1.getY()), dz = Math.abs(ruler2.getZ() - ruler1.getZ());
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            ClientState.say(String.format("📏 %.1f blocks · size %d×%d×%d · slope %.0f°", d, dx + 1, dy + 1, dz + 1, Math.toDegrees(Math.atan2(dy, Math.max(0.0001, Math.sqrt(dx * dx + dz * dz))))));
        }

        private static void guide() {
            ClientState.say("§d✦ Pride Studio tools:§f wands select · copy/cut/paste/rotate/flip work on the clipboard · arrows move or stack the selection · shape tools build where you click · buckets fill/drain · trowel, iron, brush, chisel, hammer sculpt · toolboxes open the panel and Editor Mode (`). Sizes and blocks: the panel (\\).");
        }
    }
}
