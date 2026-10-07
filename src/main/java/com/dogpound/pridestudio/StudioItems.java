package com.dogpound.pridestudio;

import com.dogpound.pridestudio.client.ClientState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/** The Studio Wand (pick corners) and the Studio Brush (VoxelSniper-style painting from far away). */
@Mod.EventBusSubscriber(modid = PrideStudio.MODID)
public final class StudioItems {
    private StudioItems() {}

    public static final Item WAND = new Tool("wand"), BRUSH = new Tool("brush");

    static final class Tool extends Item {
        Tool(String name) {
            setRegistryName(PrideStudio.MODID, name);
            setUnlocalizedName(PrideStudio.MODID + "." + name);
            setMaxStackSize(1);
            setCreativeTab(CreativeTabs.TOOLS);
            setFull3D();
        }

        /** never break blocks with these, even in creative */
        @Override public boolean canDestroyBlockInCreative(World w, BlockPos pos, ItemStack s, EntityPlayer p) { return false; }
        @Override public boolean onBlockStartBreak(ItemStack s, BlockPos pos, EntityPlayer p) { return true; }
        @Override public float getDestroySpeed(ItemStack s, IBlockState st) { return 0F; }

        @Override
        public EnumActionResult onItemUse(EntityPlayer p, World w, BlockPos pos, EnumHand hand, EnumFacing face, float hx, float hy, float hz) {
            if (w.isRemote) ClientState.toolUse(this == WAND, false);
            return EnumActionResult.SUCCESS;
        }

        @Override
        public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
            if (w.isRemote) ClientState.toolUse(this == WAND, false);
            return new ActionResult<>(EnumActionResult.SUCCESS, p.getHeldItem(hand));
        }

        @SideOnly(Side.CLIENT)
        @Override
        public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
            if (this == WAND) { tip.add("§dLeft click: corner 1"); tip.add("§dRight click: corner 2"); tip.add("§7Reaches far away blocks too"); }
            else { tip.add("§dRight click: paint with the brush"); tip.add("§dLeft click: the reverse (remove / lower / erode)"); tip.add("§7Pick the brush in the Pride Studio panel"); }
        }
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) { e.getRegistry().registerAll(WAND, BRUSH); }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void models(ModelRegistryEvent e) {
        ModelLoader.setCustomModelResourceLocation(WAND, 0, new ModelResourceLocation(WAND.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(BRUSH, 0, new ModelResourceLocation(BRUSH.getRegistryName(), "inventory"));
    }
}
