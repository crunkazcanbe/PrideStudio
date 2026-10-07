package com.dogpound.pridestudio.edit;

import net.minecraft.block.BlockFlower;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.*;

import java.util.Random;

/** Trees, flowers and grass, clearing mobs and dropped items. */
public final class NatureOps {
    private NatureOps() {}
    private static final Random RND = new Random();

    /** the top solid block of a column near y (searching 32 up and down), or null */
    static BlockPos ground(World w, int x, int y, int z) {
        for (int yy = Math.min(255, y + 32); yy >= Math.max(1, y - 32); yy--) {
            BlockPos p = new BlockPos(x, yy, z);
            IBlockState s = w.getBlockState(p);
            if (!s.getBlock().isAir(s, w, p) && !s.getBlock().isReplaceable(w, p)) return p;
        }
        return null;
    }

    static WorldGenAbstractTree tree(String type) {
        if (type.equals("mixed")) type = new String[]{"oak", "birch", "spruce", "big", "jungle", "acacia", "darkoak", "pine", "swamp"}[RND.nextInt(9)];
        switch (type) {
            case "birch": return new WorldGenBirchTree(true, false);
            case "tallbirch": return new WorldGenBirchTree(true, true);
            case "spruce": return new WorldGenTaiga2(true);
            case "pine": return new WorldGenTaiga1();
            case "big": return new WorldGenBigTree(true);
            case "jungle": return new WorldGenTrees(true, 4 + RND.nextInt(7), Blocks.LOG.getStateFromMeta(3), Blocks.LEAVES.getStateFromMeta(3), true);
            case "megajungle": return new WorldGenMegaJungle(true, 10, 20, Blocks.LOG.getStateFromMeta(3), Blocks.LEAVES.getStateFromMeta(3));
            case "acacia": return new WorldGenSavannaTree(true);
            case "darkoak": return new WorldGenCanopyTree(true);
            case "swamp": return new WorldGenSwamp();
            default: return new WorldGenTrees(true);
        }
    }

    /** trees on grass/dirt within the radius; density = percent of columns that try */
    public static int forest(EditSession es, BlockPos o, int r, int density, String type) {
        World w = es.world;
        es.beginCapture(o.add(-r - 8, -34, -r - 8), o.add(r + 8, 64, r + 8));
        int n = 0;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            if (x * x + z * z > r * r || RND.nextInt(1000) >= density * 10) continue;
            BlockPos g = ground(w, o.getX() + x, o.getY(), o.getZ() + z);
            if (g == null) continue;
            IBlockState s = w.getBlockState(g);
            if (s.getBlock() != Blocks.GRASS && s.getBlock() != Blocks.DIRT) continue;
            if (tree(type).generate(w, RND, g.up())) n++;
        }
        es.endCapture();
        return n;
    }

    /** tall grass and flowers on grass blocks */
    public static int flora(EditSession es, BlockPos o, int r, int density) {
        World w = es.world;
        int n = 0;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            if (x * x + z * z > r * r || RND.nextInt(100) >= density) continue;
            BlockPos g = ground(w, o.getX() + x, o.getY(), o.getZ() + z);
            if (g == null || w.getBlockState(g).getBlock() != Blocks.GRASS || !w.isAirBlock(g.up())) continue;
            IBlockState put;
            int roll = RND.nextInt(10);
            if (roll < 6) put = Blocks.TALLGRASS.getDefaultState().withProperty(BlockTallGrass.TYPE, BlockTallGrass.EnumType.GRASS);
            else if (roll < 8) put = Blocks.YELLOW_FLOWER.getDefaultState();
            else { BlockFlower.EnumFlowerType[] t = BlockFlower.EnumFlowerType.getTypes(BlockFlower.EnumFlowerColor.RED); put = Blocks.RED_FLOWER.getDefaultState().withProperty(Blocks.RED_FLOWER.getTypeProperty(), t[RND.nextInt(t.length)]); }
            if (es.set(g.up(), put)) n++;
        }
        return n;
    }

    /** remove monsters (or every mob that isn't a pet or named) within the radius */
    public static int butcher(World w, BlockPos o, int r, boolean all) {
        int n = 0;
        for (EntityLiving e : w.getEntitiesWithinAABB(EntityLiving.class, new AxisAlignedBB(o).grow(r))) {
            if (e.hasCustomName() || (e instanceof EntityTameable && ((EntityTameable) e).isTamed())) continue;
            if (!all && !(e instanceof IMob)) continue;
            e.setDead(); n++;
        }
        return n;
    }

    public static int removeItems(World w, BlockPos o, int r) {
        int n = 0;
        for (Entity e : w.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(o).grow(r), x -> x instanceof EntityItem || x instanceof EntityXPOrb)) { e.setDead(); n++; }
        return n;
    }

    public static int extinguishPlayers(World w, BlockPos o, int r) {
        int n = 0;
        for (EntityPlayer p : w.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(o).grow(r))) if (p.isBurning()) { p.extinguish(); n++; }
        return n;
    }
}
