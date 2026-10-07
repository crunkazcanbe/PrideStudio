package com.dogpound.pridestudio.edit;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Her favourite (2026-10-04): stand where the water should go, pick how far and how deep, done — no selection.
 * fill: spreads sideways only on your own layer and straight down from there (follows the ground's shape, never above you).
 * fillRecursive: also spreads sideways on every layer below (fills overhangs and caves under the surface).
 * Plus drain, fix water/lava, remove near/above/below, replace near, snow, thaw, green, extinguish.
 */
public final class WaterOps {
    private WaterOps() {}

    private static boolean holeAt(World w, BlockPos p) {
        IBlockState s = w.getBlockState(p);
        if (s.getBlock().isAir(s, w, p)) return true;
        return s.getBlock() instanceof BlockLiquid && s.getValue(BlockLiquid.LEVEL) != 0;      // flowing water/lava counts as a hole
    }

    private static boolean inSphere(BlockPos o, BlockPos p, int r) {
        int dx = p.getX() - o.getX(), dy = p.getY() - o.getY(), dz = p.getZ() - o.getZ();
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    /** //fill <pattern> <radius> [depth]  and  //fillr (recursive = spread on every layer, not only the top one) */
    public static int fill(EditSession es, BlockPos origin, Pattern pat, int radius, int depth, boolean recursive) {
        World w = es.world;
        if (!holeAt(w, origin)) return 0;
        int minY = Math.max(0, origin.getY() - Math.max(1, depth) + 1), topY = origin.getY();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(origin); seen.add(origin);
        int n = 0;
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (!es.set(p, pat.next())) break;
            n++;
            for (EnumFacing f : EnumFacing.values()) {
                if (f == EnumFacing.UP) continue;
                if (f != EnumFacing.DOWN && !recursive && p.getY() != topY) continue;      // sideways only on the top layer
                BlockPos q = p.offset(f);
                if (q.getY() < minY || q.getY() > topY || !inSphere(origin, q, radius) || !seen.add(q)) continue;
                if (holeAt(w, q)) queue.add(q);
            }
        }
        return n;
    }

    /** remove water and lava connected to you (or the nearest within 3 blocks), within the radius */
    public static int drain(EditSession es, BlockPos origin, int radius) {
        World w = es.world;
        BlockPos seed = null;
        for (BlockPos p : BlockPos.getAllInBox(origin.add(-3, -3, -3), origin.add(3, 3, 3)))
            if (w.getBlockState(p).getMaterial().isLiquid() && (seed == null || p.distanceSq(origin) < seed.distanceSq(origin))) seed = p.toImmutable();
        if (seed == null) return 0;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(seed); seen.add(seed);
        int n = 0;
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (!es.set(p, Blocks.AIR.getDefaultState())) break;
            n++;
            for (EnumFacing f : EnumFacing.values()) {
                BlockPos q = p.offset(f);
                if (!inSphere(origin, q, radius) || !seen.add(q)) continue;
                if (w.getBlockState(q).getMaterial().isLiquid()) queue.add(q);
            }
        }
        return n;
    }

    /** make water (or lava) still: flowing blocks become sources, and gaps up to your level connected to it fill in */
    public static int fixLiquid(EditSession es, BlockPos origin, int radius, boolean lava) {
        World w = es.world;
        Material m = lava ? Material.LAVA : Material.WATER;
        IBlockState still = (lava ? Blocks.LAVA : Blocks.WATER).getDefaultState();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos p : BlockPos.getAllInBox(origin.add(-radius, -radius, -radius), origin.add(radius, 0, radius))) {
            BlockPos q = p.toImmutable();
            if (inSphere(origin, q, radius) && w.getBlockState(q).getMaterial() == m && seen.add(q)) queue.add(q);
        }
        int n = 0;
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            IBlockState s = w.getBlockState(p);
            if (s != still) { if (!es.set(p, still)) break; n++; }
            for (EnumFacing f : EnumFacing.values()) {
                if (f == EnumFacing.UP) continue;
                BlockPos q = p.offset(f);
                if (q.getY() > origin.getY() || !inSphere(origin, q, radius) || !seen.add(q)) continue;
                IBlockState t = w.getBlockState(q);
                if (t.getBlock().isAir(t, w, q) || t.getMaterial() == m) queue.add(q);
            }
        }
        return n;
    }

    /** remove every block of these kinds in a cube round you */
    public static int removeNear(EditSession es, BlockPos o, Pattern which, int size) {
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBox(o.add(-size, -size, -size), o.add(size, size, size)))
            if (which.contains(es.world.getBlockState(p))) { if (!es.set(p, Blocks.AIR.getDefaultState())) break; n++; }
        return n;
    }

    /** clear a square column above (up = true) or below you */
    public static int removeColumn(EditSession es, BlockPos o, int size, int height, boolean up) {
        int n = 0;
        int y0 = up ? o.getY() : Math.max(0, o.getY() - height), y1 = up ? Math.min(255, o.getY() + height) : o.getY() - 1;
        for (BlockPos p : BlockPos.getAllInBox(new BlockPos(o.getX() - size, y0, o.getZ() - size), new BlockPos(o.getX() + size, y1, o.getZ() + size))) {
            if (es.world.isAirBlock(p)) continue;
            if (!es.set(p, Blocks.AIR.getDefaultState())) break;
            n++;
        }
        return n;
    }

    /** swap one set of blocks for another in a cube round you */
    public static int replaceNear(EditSession es, BlockPos o, Pattern from, Pattern to, int size) {
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBox(o.add(-size, -size, -size), o.add(size, size, size)))
            if (from.contains(es.world.getBlockState(p))) { if (!es.set(p, to.next())) break; n++; }
        return n;
    }

    /** snow: a snow layer on every top surface + freeze still water; thaw: the reverse */
    public static int snow(EditSession es, BlockPos o, int radius, boolean thaw) {
        World w = es.world;
        int n = 0;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (x * x + z * z > radius * radius) continue;
            BlockPos top = w.getHeight(o.add(x, 0, z));
            BlockPos below = top.down();
            IBlockState b = w.getBlockState(below);
            if (thaw) {
                if (b.getBlock() == Blocks.SNOW_LAYER) { es.set(below, Blocks.AIR.getDefaultState()); n++; }
                else if (b.getBlock() == Blocks.ICE) { es.set(below, Blocks.WATER.getDefaultState()); n++; }
            } else {
                if (b.getBlock() == Blocks.WATER && b.getValue(BlockLiquid.LEVEL) == 0) { es.set(below, Blocks.ICE.getDefaultState()); n++; }
                else if (b.isSideSolid(w, below, EnumFacing.UP) && w.isAirBlock(top)) { es.set(top, Blocks.SNOW_LAYER.getDefaultState()); n++; }
            }
        }
        return n;
    }

    /** top dirt becomes grass */
    public static int green(EditSession es, BlockPos o, int radius) {
        World w = es.world;
        int n = 0;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (x * x + z * z > radius * radius) continue;
            BlockPos below = w.getHeight(o.add(x, 0, z)).down();
            if (w.getBlockState(below).getBlock() == Blocks.DIRT) { es.set(below, Blocks.GRASS.getDefaultState()); n++; }
        }
        return n;
    }

    /** put out fires */
    public static int extinguish(EditSession es, BlockPos o, int radius) {
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBox(o.add(-radius, -radius, -radius), o.add(radius, radius, radius)))
            if (es.world.getBlockState(p).getBlock() == Blocks.FIRE) { es.set(p, Blocks.AIR.getDefaultState()); n++; }
        return n;
    }
}
