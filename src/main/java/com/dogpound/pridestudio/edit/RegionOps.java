package com.dogpound.pridestudio.edit;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Everything that works on the selected box (WorldEdit's region commands, as panel buttons). */
public final class RegionOps {
    private RegionOps() {}

    static boolean air(World w, BlockPos p) { IBlockState s = w.getBlockState(p); return s.getBlock().isAir(s, w, p); }

    /** place only where the mask (if any) matches what's there now */
    static boolean put(EditSession es, BlockPos p, IBlockState s, Pattern mask) {
        if (mask != null && !mask.contains(es.world.getBlockState(p))) return true;
        return es.set(p, s);
    }

    public static int set(EditSession es, BlockPos a, BlockPos b, Pattern pat, Pattern mask) {
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBoxMutable(a, b)) { int before = es.size(); if (!put(es, p, pat.next(), mask)) break; if (es.size() > before) n++; }
        return n;
    }

    public static int walls(EditSession es, BlockPos a, BlockPos b, Pattern pat, boolean floorAndCeiling) {
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBoxMutable(a, b)) {
            boolean side = p.getX() == a.getX() || p.getX() == b.getX() || p.getZ() == a.getZ() || p.getZ() == b.getZ();
            boolean cap = floorAndCeiling && (p.getY() == a.getY() || p.getY() == b.getY());
            if ((side || cap) && es.set(p, pat.next())) n++;
        }
        return n;
    }

    /** empty the inside, keeping a shell `thick` blocks thick */
    public static int hollow(EditSession es, BlockPos a, BlockPos b, int thick, Pattern fill) {
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBoxMutable(a, b)) {
            int d = Math.min(Math.min(Math.min(p.getX() - a.getX(), b.getX() - p.getX()), Math.min(p.getY() - a.getY(), b.getY() - p.getY())), Math.min(p.getZ() - a.getZ(), b.getZ() - p.getZ()));
            if (d >= thick && es.set(p, fill == null ? Blocks.AIR.getDefaultState() : fill.next())) n++;
        }
        return n;
    }

    /** a layer on top of the highest block in each column */
    public static int overlay(EditSession es, BlockPos a, BlockPos b, Pattern pat, int layers) {
        int n = 0;
        for (int x = a.getX(); x <= b.getX(); x++) for (int z = a.getZ(); z <= b.getZ(); z++) {
            for (int y = b.getY(); y >= a.getY(); y--) {
                if (air(es.world, new BlockPos(x, y, z))) continue;
                for (int i = 1; i <= layers && y + i <= 255; i++) if (es.set(new BlockPos(x, y + i, z), pat.next())) n++;
                break;
            }
        }
        return n;
    }

    /** grass on top, 3 dirt, stone below — only on natural ground blocks */
    public static int naturalize(EditSession es, BlockPos a, BlockPos b) {
        int n = 0;
        for (int x = a.getX(); x <= b.getX(); x++) for (int z = a.getZ(); z <= b.getZ(); z++) {
            int depth = -1;
            for (int y = b.getY(); y >= a.getY(); y--) {
                BlockPos p = new BlockPos(x, y, z);
                IBlockState s = es.world.getBlockState(p);
                Material m = s.getMaterial();
                if (s.getBlock().isAir(s, es.world, p) || m.isLiquid()) { depth = -1; continue; }
                depth++;
                if (m != Material.GRASS && m != Material.GROUND && m != Material.ROCK) continue;
                IBlockState to = depth == 0 ? Blocks.GRASS.getDefaultState() : depth <= 3 ? Blocks.DIRT.getDefaultState() : Blocks.STONE.getDefaultState();
                if (s != to && es.set(p, to)) n++;
            }
        }
        return n;
    }

    /** soften the ground: blur the height of every column, then raise/lower each column to its new height */
    public static int smooth(EditSession es, BlockPos a, BlockPos b, int iterations) {
        World w = es.world;
        int sx = b.getX() - a.getX() + 1, sz = b.getZ() - a.getZ() + 1;
        int[][] h = new int[sx][sz];
        IBlockState[][] top = new IBlockState[sx][sz];
        for (int i = 0; i < sx; i++) for (int k = 0; k < sz; k++) {
            h[i][k] = a.getY() - 1;
            for (int y = b.getY(); y >= a.getY(); y--) {
                BlockPos p = new BlockPos(a.getX() + i, y, a.getZ() + k);
                IBlockState s = w.getBlockState(p);
                if (s.getMaterial().blocksMovement() && s.isFullCube()) { h[i][k] = y; top[i][k] = s; break; }
            }
        }
        double[][] f = new double[sx][sz];
        for (int i = 0; i < sx; i++) for (int k = 0; k < sz; k++) f[i][k] = h[i][k];
        for (int it = 0; it < Math.max(1, iterations); it++) {
            double[][] g = new double[sx][sz];
            for (int i = 0; i < sx; i++) for (int k = 0; k < sz; k++) {
                double sum = 0, wsum = 0;
                for (int di = -1; di <= 1; di++) for (int dk = -1; dk <= 1; dk++) {
                    int ii = i + di, kk = k + dk;
                    if (ii < 0 || kk < 0 || ii >= sx || kk >= sz) continue;
                    double wt = di == 0 && dk == 0 ? 4 : di == 0 || dk == 0 ? 2 : 1;
                    sum += f[ii][kk] * wt; wsum += wt;
                }
                g[i][k] = sum / wsum;
            }
            f = g;
        }
        int n = 0;
        for (int i = 0; i < sx; i++) for (int k = 0; k < sz; k++) {
            if (top[i][k] == null) continue;
            int x = a.getX() + i, z = a.getZ() + k, old = h[i][k], nu = Math.max(a.getY(), Math.min(b.getY(), (int) Math.round(f[i][k])));
            if (nu > old) {
                IBlockState under = w.getBlockState(new BlockPos(x, old - 1, z));
                if (!under.getMaterial().blocksMovement()) under = top[i][k];
                for (int y = old; y < nu; y++) if (es.set(new BlockPos(x, y, z), under)) n++;
                if (es.set(new BlockPos(x, nu, z), top[i][k])) n++;
            } else if (nu < old) {
                for (int y = old; y > nu; y--) if (es.set(new BlockPos(x, y, z), Blocks.AIR.getDefaultState())) n++;
                if (es.set(new BlockPos(x, nu, z), top[i][k])) n++;
            }
        }
        return n;
    }

    static final class Snap { final Map<BlockPos, IBlockState> s = new LinkedHashMap<>(); final Map<BlockPos, NBTTagCompound> te = new HashMap<>(); }

    static Snap snap(World w, BlockPos a, BlockPos b) {
        Snap s = new Snap();
        for (BlockPos p : BlockPos.getAllInBox(a, b)) {
            s.s.put(p, w.getBlockState(p));
            TileEntity t = w.getTileEntity(p);
            if (t != null) try { s.te.put(p, t.writeToNBT(new NBTTagCompound())); } catch (Throwable ignored) { }
        }
        return s;
    }

    /** repeat the box `count` times in a direction */
    public static int stack(EditSession es, BlockPos a, BlockPos b, EnumFacing f, int count, boolean skipAir) {
        Snap s = snap(es.world, a, b);
        int len = f.getAxis() == EnumFacing.Axis.X ? b.getX() - a.getX() + 1 : f.getAxis() == EnumFacing.Axis.Y ? b.getY() - a.getY() + 1 : b.getZ() - a.getZ() + 1;
        int n = 0;
        for (int i = 1; i <= count; i++)
            for (Map.Entry<BlockPos, IBlockState> e : s.s.entrySet()) {
                if (skipAir && e.getValue().getBlock() == Blocks.AIR) continue;
                BlockPos to = e.getKey().offset(f, len * i);
                if (!es.set(to, e.getValue())) return n;
                es.copyTe(s.te.get(e.getKey()), to);
                n++;
            }
        return n;
    }

    /** move the box; what's left behind becomes `leave` (air by default) */
    public static int move(EditSession es, BlockPos a, BlockPos b, EnumFacing f, int dist, Pattern leave, boolean skipAir) {
        Snap s = snap(es.world, a, b);
        int n = 0;
        for (BlockPos p : s.s.keySet()) es.set(p, leave == null ? Blocks.AIR.getDefaultState() : leave.next());
        for (Map.Entry<BlockPos, IBlockState> e : s.s.entrySet()) {
            if (skipAir && e.getValue().getBlock() == Blocks.AIR) continue;
            BlockPos to = e.getKey().offset(f, dist);
            if (!es.set(to, e.getValue())) break;
            es.copyTe(s.te.get(e.getKey()), to);
            n++;
        }
        return n;
    }

    /** a straight line between the two corners, `thick` blocks round */
    public static int line(EditSession es, BlockPos a, BlockPos b, Pattern pat, int thick) {
        int steps = Math.max(Math.abs(b.getX() - a.getX()), Math.max(Math.abs(b.getY() - a.getY()), Math.abs(b.getZ() - a.getZ())));
        int n = 0, r = Math.max(0, thick - 1);
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            BlockPos c = new BlockPos(Math.round(a.getX() + (b.getX() - a.getX()) * t), Math.round(a.getY() + (b.getY() - a.getY()) * t), Math.round(a.getZ() + (b.getZ() - a.getZ()) * t));
            for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++)
                if (dx * dx + dy * dy + dz * dz <= r * r && es.set(c.add(dx, dy, dz), pat.next())) n++;
        }
        return n;
    }

    public static int center(EditSession es, BlockPos a, BlockPos b, Pattern pat) {
        int n = 0;
        int x0 = (a.getX() + b.getX()) / 2, y0 = (a.getY() + b.getY()) / 2, z0 = (a.getZ() + b.getZ()) / 2;
        int x1 = (a.getX() + b.getX() + 1) / 2, y1 = (a.getY() + b.getY() + 1) / 2, z1 = (a.getZ() + b.getZ() + 1) / 2;
        for (BlockPos p : BlockPos.getAllInBox(new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1))) if (es.set(p, pat.next())) n++;
        return n;
    }

    public static int count(World w, BlockPos a, BlockPos b, Pattern mask) {
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBoxMutable(a, b)) if (mask == null ? !air(w, p) : mask.contains(w.getBlockState(p))) n++;
        return n;
    }

    public static int drainBox(EditSession es, BlockPos a, BlockPos b) {
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBoxMutable(a, b)) if (es.world.getBlockState(p).getMaterial().isLiquid() && es.set(p, Blocks.AIR.getDefaultState())) n++;
        return n;
    }

    /** change the biome of every column in the box (not undoable — biomes aren't blocks) */
    public static String setBiome(World w, BlockPos a, BlockPos b, String name) {
        Biome bio = Biome.REGISTRY.getObject(new ResourceLocation(name.contains(":") ? name : "minecraft:" + name));
        if (bio == null) for (Biome x : Biome.REGISTRY) if (x.getBiomeName().equalsIgnoreCase(name) || x.getRegistryName().getResourcePath().equalsIgnoreCase(name)) bio = x;
        if (bio == null) return "§cUnknown biome: " + name;
        byte id = (byte) Biome.getIdForBiome(bio);
        java.util.Set<Chunk> touched = new java.util.HashSet<>();
        int n = 0;
        for (int x = a.getX(); x <= b.getX(); x++) for (int z = a.getZ(); z <= b.getZ(); z++) {
            Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
            c.getBiomeArray()[(z & 15) << 4 | (x & 15)] = id;
            c.markDirty();
            touched.add(c);
            n++;
        }
        for (Chunk c : touched)
            for (net.minecraft.entity.player.EntityPlayer pl : w.playerEntities)
                if (pl instanceof EntityPlayerMP && Math.abs((((int) pl.posX) >> 4) - c.x) < 16 && Math.abs((((int) pl.posZ) >> 4) - c.z) < 16)
                    ((EntityPlayerMP) pl).connection.sendPacket(new SPacketChunkData(c, 65535));
        return "✦ Biome set to " + bio.getBiomeName() + " on " + n + " columns";
    }
}
