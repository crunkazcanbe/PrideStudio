package com.dogpound.pridestudio.edit;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Editor Mode's sculpt and paint tools (Axiom-style, clean-room): Gaussian smooth / weld / melt on a density
 * field, noise rocks, noise and gradient painters, heightmap elevation with falloff, distort, roughen, shatter,
 * extrude, flood fill, fluid pour, biome brush. Every tool honours the brush shape (sphere, cube, octahedron,
 * cylinder), "surface only", "keep existing" and the block mask.
 *
 * Options arrive as NBT keys: shape, size (radius), strength, seed, scale, blocks (comma list), block2,
 * surface, keep, mode, rate, falloff, ratio, width, count, limit, biome.
 */
public final class SculptOps {
    private SculptOps() {}

    static final IBlockState AIR = Blocks.AIR.getDefaultState();

    public static final String[] TOOLS = {"painter", "noisepaint", "gradient", "biomebrush", "rock", "weld", "melt", "gsmooth",
            "elevation", "distort", "roughen", "shatter", "extrude", "floodfill", "pour", "freehand"};

    public static boolean has(String tool) {
        for (String t : TOOLS) if (t.equals(tool)) return true;
        return false;
    }

    // ---------------------------------------------------------------- shapes and helpers

    static boolean inShape(String shape, int x, int y, int z, int r) {
        double R = r + 0.5;
        switch (shape) {
            case "cube": return true;
            case "octahedron": return Math.abs(x) + Math.abs(y) + Math.abs(z) <= r;
            case "cylinder": return x * x + z * z <= R * R && Math.abs(y) <= r;
            case "disc": return x * x + z * z <= R * R && y == 0;
            default: return x * x + y * y + z * z <= R * R;
        }
    }

    static boolean solid(World w, BlockPos p) {
        IBlockState s = w.getBlockState(p);
        return !s.getBlock().isAir(s, w, p) && !s.getMaterial().isLiquid() && !s.getBlock().isReplaceable(w, p);
    }

    static boolean exposed(World w, BlockPos p) {
        for (EnumFacing f : EnumFacing.values()) if (!solid(w, p.offset(f))) return true;
        return false;
    }

    /** the most common solid neighbour (what a filled hole should be made of) */
    static IBlockState neighbourMaterial(World w, BlockPos p, IBlockState fallback) {
        Map<IBlockState, Integer> votes = new HashMap<>();
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            BlockPos q = p.add(dx, dy, dz);
            if (solid(w, q)) votes.merge(w.getBlockState(q), dy > 0 ? 1 : 2, Integer::sum);   // prefer what's beside/below
        }
        IBlockState best = fallback;
        int n = 0;
        for (Map.Entry<IBlockState, Integer> e : votes.entrySet()) if (e.getValue() > n) { n = e.getValue(); best = e.getKey(); }
        return best;
    }

    static boolean put(EditSession es, BlockPos p, IBlockState s, Pattern mask) { return RegionOps.put(es, p, s, mask); }

    // ---------------------------------------------------------------- noise (Perlin, seeded)

    static final class Noise {
        private final int[] perm = new int[512];

        Noise(long seed) {
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) p[i] = i;
            Random r = new Random(seed);
            for (int i = 255; i > 0; i--) { int j = r.nextInt(i + 1); int t = p[i]; p[i] = p[j]; p[j] = t; }
            for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
        }

        private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }

        private static double grad(int h, double x, double y, double z) {
            int k = h & 15;
            double u = k < 8 ? x : y, v = k < 4 ? y : k == 12 || k == 14 ? x : z;
            return ((k & 1) == 0 ? u : -u) + ((k & 2) == 0 ? v : -v);
        }

        /** -1..1 */
        double at(double x, double y, double z) {
            int X = MathHelper.floor(x) & 255, Y = MathHelper.floor(y) & 255, Z = MathHelper.floor(z) & 255;
            x -= Math.floor(x); y -= Math.floor(y); z -= Math.floor(z);
            double u = fade(x), v = fade(y), w = fade(z);
            int A = perm[X] + Y, AA = perm[A] + Z, AB = perm[A + 1] + Z, B = perm[X + 1] + Y, BA = perm[B] + Z, BB = perm[B + 1] + Z;
            return lerp(w, lerp(v, lerp(u, grad(perm[AA], x, y, z), grad(perm[BA], x - 1, y, z)),
                            lerp(u, grad(perm[AB], x, y - 1, z), grad(perm[BB], x - 1, y - 1, z))),
                    lerp(v, lerp(u, grad(perm[AA + 1], x, y, z - 1), grad(perm[BA + 1], x - 1, y, z - 1)),
                            lerp(u, grad(perm[AB + 1], x, y - 1, z - 1), grad(perm[BB + 1], x - 1, y - 1, z - 1))));
        }

        /** fractal: octaves layered, 0..1 */
        double fbm(double x, double y, double z, int octaves) {
            double sum = 0, amp = 1, f = 1, norm = 0;
            for (int i = 0; i < Math.max(1, octaves); i++) { sum += at(x * f, y * f, z * f) * amp; norm += amp; amp *= 0.5; f *= 2; }
            return MathHelper.clamp(sum / norm * 0.5 + 0.5, 0, 1);
        }

        private static double lerp(double t, double a, double b) { return a + t * (b - a); }
    }

    // ---------------------------------------------------------------- dispatcher

    public static int run(EditSession es, String tool, BlockPos t, EnumFacing face, NBTTagCompound a, Pattern pat, Pattern mask, boolean alt) {
        World w = es.world;
        int r = MathHelper.clamp(a.hasKey("size") ? a.getInteger("size") : 5, 0, 64);
        int strength = MathHelper.clamp(a.hasKey("strength") ? a.getInteger("strength") : 2, 1, 16);
        String shape = a.getString("shape").isEmpty() ? "sphere" : a.getString("shape");
        boolean surface = a.getBoolean("surface"), keep = a.getBoolean("keep");
        long seed = a.hasKey("seed") && a.getLong("seed") != 0 ? a.getLong("seed") : t.toLong() ^ System.nanoTime();
        double scale = a.hasKey("scale") ? Math.max(0.5, a.getInteger("scale")) : 8;
        switch (tool) {
            case "freehand": return freehand(es, t, r, shape, pat, mask, keep, alt);
            case "painter": return painter(es, t, r, shape, pat, mask, surface);
            case "noisepaint": return noisePaint(es, t, r, shape, a.getString("blocks"), pat, mask, surface, new Noise(a.getLong("seed")), scale, a.getInteger("octaves"));
            case "gradient": return gradient(es, t, r, shape, pat, a.getString("block2"), mask, surface, a.getString("mode"));
            case "biomebrush": return biome(w, t, r, a.getString("biome"));
            case "rock": return rock(es, t, r, pat, mask, keep, new Noise(seed), Math.max(1, strength), alt);
            case "weld": case "melt": case "gsmooth":
                return gaussian(es, t, r, shape, tool, strength, a.hasKey("ratio") ? a.getInteger("ratio") : 100, pat, mask, a.getBoolean("useActive"));
            case "elevation": return elevation(es, t, r, a.getString("mode").isEmpty() ? (alt ? "lower" : "raise") : a.getString("mode"), strength, a.getString("falloff"));
            case "distort": return distort(es, t, r, shape, new Noise(seed), scale, Math.max(1, strength), mask);
            case "roughen": return roughen(es, t, r, shape, Math.max(1, Math.min(5, strength)), a.hasKey("ratio") ? a.getInteger("ratio") : 50, new Random(seed), mask);
            case "shatter": return shatter(es, t, r, shape, scale, Math.max(1, a.hasKey("width") ? a.getInteger("width") : 1), seed, a.getBoolean("useActive") ? pat : null, mask);
            case "extrude": return extrude(es, t, face == null ? EnumFacing.UP : face, r, Math.max(1, a.hasKey("count") ? a.getInteger("count") : 1), alt, a.getString("compare"));
            case "floodfill": return flood(es, face == null ? t.up() : t.offset(face), a.hasKey("limit") ? a.getInteger("limit") : 20000, pat, a.getBoolean("up"));
            case "pour": return pour(es, t, r, pat);
            default: return 0;
        }
    }

    // ---------------------------------------------------------------- painting

    static int freehand(EditSession es, BlockPos t, int r, String shape, Pattern pat, Pattern mask, boolean keep, boolean alt) {
        int n = 0;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            if (!inShape(shape, x, y, z, r)) continue;
            BlockPos p = t.add(x, y, z);
            if (alt) { if (solid(es.world, p) && put(es, p, AIR, mask)) n++; continue; }
            if (keep && solid(es.world, p)) continue;
            if (!put(es, p, pat.next(), mask)) return n;
            n++;
        }
        return n;
    }

    static int painter(EditSession es, BlockPos t, int r, String shape, Pattern pat, Pattern mask, boolean surface) {
        int n = 0;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            if (!inShape(shape, x, y, z, r)) continue;
            BlockPos p = t.add(x, y, z);
            if (!solid(es.world, p) || (surface && !exposed(es.world, p))) continue;
            if (!put(es, p, pat.next(), mask)) return n;
            n++;
        }
        return n;
    }

    static int noisePaint(EditSession es, BlockPos t, int r, String shape, String blocks, Pattern fallback, Pattern mask, boolean surface, Noise noise, double scale, int octaves) {
        List<IBlockState> list = new ArrayList<>();
        for (String s : blocks.split(",")) { IBlockState st = s.trim().isEmpty() ? null : Pattern.state(s.trim()); if (st != null) list.add(st); }
        if (list.isEmpty()) list.add(fallback.next());
        int n = 0;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            if (!inShape(shape, x, y, z, r)) continue;
            BlockPos p = t.add(x, y, z);
            if (!solid(es.world, p) || (surface && !exposed(es.world, p))) continue;
            double v = noise.fbm(p.getX() / scale, p.getY() / scale, p.getZ() / scale, Math.max(1, octaves));
            IBlockState st = list.get(Math.min(list.size() - 1, (int) (v * list.size())));
            if (!put(es, p, st, mask)) return n;
            n++;
        }
        return n;
    }

    static int gradient(EditSession es, BlockPos t, int r, String shape, Pattern top, String bottomName, Pattern mask, boolean surface, String mode) {
        IBlockState bottom = Pattern.state(bottomName.isEmpty() ? "stone" : bottomName);
        if (bottom == null) bottom = Blocks.STONE.getDefaultState();
        Random rnd = new Random(t.toLong());
        boolean radial = "radial".equals(mode);
        int n = 0;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            if (!inShape(shape, x, y, z, r)) continue;
            BlockPos p = t.add(x, y, z);
            if (!solid(es.world, p) || (surface && !exposed(es.world, p))) continue;
            double f = radial ? Math.sqrt(x * x + y * y + z * z) / (r + 0.5) : (y + r) / (2.0 * r + 1);   // 0 = top block, 1 = bottom block
            if (!radial) f = 1 - f;
            IBlockState s = rnd.nextDouble() < f ? bottom : top.next();                                        // dithered blend
            if (!put(es, p, s, mask)) return n;
            n++;
        }
        return n;
    }

    static int biome(World w, BlockPos t, int r, String biome) {
        String res = RegionOps.setBiome(w, t.add(-r, 0, -r), t.add(r, 0, r), biome.isEmpty() ? "plains" : biome);
        return res.startsWith("§c") ? 0 : (2 * r + 1) * (2 * r + 1);
    }

    // ---------------------------------------------------------------- drawing

    /** a lumpy noise boulder of the active block, melded into the ground (Ctrl: carve a lumpy hole) */
    static int rock(EditSession es, BlockPos t, int r, Pattern pat, Pattern mask, boolean keep, Noise noise, int roughness, boolean alt) {
        int R = (int) Math.ceil(r * 1.5) + 1, n = 0;
        double sc = Math.max(2, r * 0.6);
        for (int x = -R; x <= R; x++) for (int y = -R; y <= R; y++) for (int z = -R; z <= R; z++) {
            double d = Math.sqrt(x * x + y * y * 1.25 + z * z);
            double lump = 1 + (noise.fbm(x / sc, y / sc, z / sc, 3) - 0.5) * 0.35 * roughness;
            if (d > (r + 0.5) * lump) continue;
            BlockPos p = t.add(x, y, z);
            if (alt) { if (solid(es.world, p) && put(es, p, AIR, mask)) n++; continue; }
            if (keep && solid(es.world, p)) continue;
            if (!put(es, p, pat.next(), mask)) return n;
            n++;
        }
        return n;
    }

    /**
     * Gaussian density smoothing. weld = only add mass, melt = only remove, gsmooth = both, keeping the block count
     * (ratio % of removed blocks get added back: 100 = same mass, 110 = grow 10%, 90 = shrink).
     */
    static int gaussian(EditSession es, BlockPos t, int r, String shape, String tool, int strength, int ratio, Pattern pat, Pattern mask, boolean useActive) {
        World w = es.world;
        double sigma = 0.6 + strength * 0.45;
        int k = (int) Math.ceil(sigma * 2), R = r + k + 1, S = 2 * R + 1;
        float[][][] d = new float[S][S][S], tmp = new float[S][S][S];
        for (int x = 0; x < S; x++) for (int y = 0; y < S; y++) for (int z = 0; z < S; z++)
            d[x][y][z] = solid(w, t.add(x - R, y - R, z - R)) ? 1 : 0;
        double[] ker = new double[2 * k + 1];
        double sum = 0;
        for (int i = -k; i <= k; i++) { ker[i + k] = Math.exp(-(i * i) / (2 * sigma * sigma)); sum += ker[i + k]; }
        for (int i = 0; i < ker.length; i++) ker[i] /= sum;
        blur(d, tmp, ker, k, 0); blur(tmp, d, ker, k, 1); blur(d, tmp, ker, k, 2);   // result in tmp
        List<BlockPos> add = new ArrayList<>(), rem = new ArrayList<>();
        List<Float> addV = new ArrayList<>(), remV = new ArrayList<>();
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            if (!inShape(shape, x, y, z, r)) continue;
            float v = tmp[x + R][y + R][z + R];
            BlockPos p = t.add(x, y, z);
            boolean s = solid(w, p);
            if (!s && v > 0.5f && !tool.equals("melt")) { add.add(p); addV.add(v); }
            if (s && v < 0.5f && !tool.equals("weld")) { rem.add(p); remV.add(v); }
        }
        if (tool.equals("gsmooth")) {                                   // keep the mass: strongest candidates first
            sortBy(add, addV, true);
            sortBy(rem, remV, false);
            int want = (int) Math.round(rem.size() * ratio / 100.0);
            if (add.size() > want) add = add.subList(0, want);
        }
        int n = 0;
        IBlockState active = pat.next();
        for (BlockPos p : add) { if (!put(es, p, useActive ? active : neighbourMaterial(w, p, active), mask)) return n; n++; }
        for (BlockPos p : rem) { if (!put(es, p, AIR, mask)) return n; n++; }
        return n;
    }

    private static void blur(float[][][] src, float[][][] dst, double[] ker, int k, int axis) {
        int S = src.length;
        for (int x = 0; x < S; x++) for (int y = 0; y < S; y++) for (int z = 0; z < S; z++) {
            double v = 0;
            for (int i = -k; i <= k; i++) {
                int xx = x + (axis == 0 ? i : 0), yy = y + (axis == 1 ? i : 0), zz = z + (axis == 2 ? i : 0);
                if (xx < 0 || yy < 0 || zz < 0 || xx >= S || yy >= S || zz >= S) { v += ker[i + k] * src[x][y][z]; continue; }
                v += ker[i + k] * src[xx][yy][zz];
            }
            dst[x][y][z] = (float) v;
        }
    }

    private static void sortBy(List<BlockPos> ps, List<Float> vs, boolean desc) {
        Integer[] idx = new Integer[ps.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> desc ? Float.compare(vs.get(b), vs.get(a)) : Float.compare(vs.get(a), vs.get(b)));
        List<BlockPos> out = new ArrayList<>();
        for (Integer i : idx) out.add(ps.get(i));
        ps.clear();
        ps.addAll(out);
    }

    // ---------------------------------------------------------------- heightmap

    /**
     * World-Painter style elevation on columns. raise/lower by `rate` at the centre fading to 0 at the edge
     * (falloff: smooth / linear / sharp / flat); flatten pulls every column toward the clicked height;
     * smooth pulls each column toward its neighbours' average.
     */
    static int elevation(EditSession es, BlockPos t, int r, String mode, int rate, String falloff) {
        World w = es.world;
        Map<Long, Integer> h = new HashMap<>();
        int R = r + 2;
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
            BlockPos g = NatureOps.ground(w, t.getX() + x, t.getY(), t.getZ() + z);
            if (g != null) h.put(key(x, z), g.getY());
        }
        int n = 0;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            double dist = Math.sqrt(x * x + z * z) / (r + 0.5);
            if (dist > 1) continue;
            Integer cur = h.get(key(x, z));
            if (cur == null) continue;
            double f = weight(dist, falloff);
            double target;
            switch (mode) {
                case "lower": target = cur - rate * f; break;
                case "flatten": target = cur + (t.getY() - cur) * Math.min(1, f * rate / 4.0); break;
                case "smooth": {
                    double s = 0; int c = 0;
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) { Integer v = h.get(key(x + dx, z + dz)); if (v != null) { s += v; c++; } }
                    target = cur + (s / c - cur) * Math.min(1, f * rate / 3.0);
                    break;
                }
                default: target = cur + rate * f;
            }
            int to = (int) Math.round(target);
            if (to == cur) continue;
            BlockPos g = new BlockPos(t.getX() + x, cur, t.getZ() + z);
            IBlockState top = w.getBlockState(g), under = w.getBlockState(g.down());
            if (!under.getMaterial().blocksMovement()) under = top;
            if (to > cur) {
                for (int y = cur; y < to; y++) if (es.set(new BlockPos(g.getX(), y, g.getZ()), under)) n++;
                if (es.set(new BlockPos(g.getX(), to, g.getZ()), top)) n++;
            } else {
                for (int y = cur; y > to; y--) if (es.set(new BlockPos(g.getX(), y, g.getZ()), AIR)) n++;
                if (es.set(new BlockPos(g.getX(), to, g.getZ()), top)) n++;
            }
        }
        return n;
    }

    static double weight(double d, String falloff) {
        switch (falloff == null ? "" : falloff) {
            case "linear": return 1 - d;
            case "sharp": return (1 - d) * (1 - d);
            case "flat": return d < 0.85 ? 1 : (1 - d) / 0.15;
            default: return (Math.cos(Math.PI * d) + 1) / 2;                      // smooth
        }
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    // ---------------------------------------------------------------- manipulation

    /** push blocks around by a noise field (domain warp) */
    static int distort(EditSession es, BlockPos t, int r, String shape, Noise noise, double scale, int dist, Pattern mask) {
        World w = es.world;
        int R = r + dist + 1;
        Map<BlockPos, IBlockState> snap = new HashMap<>();
        for (BlockPos p : BlockPos.getAllInBox(t.add(-R, -R, -R), t.add(R, R, R))) snap.put(p, w.getBlockState(p));
        int n = 0;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            if (!inShape(shape, x, y, z, r)) continue;
            BlockPos p = t.add(x, y, z);
            double sx = p.getX() / scale, sy = p.getY() / scale, sz = p.getZ() / scale;
            int ox = (int) Math.round(noise.at(sx, sy, sz) * dist), oy = (int) Math.round(noise.at(sx + 31.7, sy, sz) * dist), oz = (int) Math.round(noise.at(sx, sy + 57.3, sz) * dist);
            IBlockState src = snap.get(p.add(ox, oy, oz));
            if (src == null || src == snap.get(p)) continue;
            if (!put(es, p, src, mask)) return n;
            n++;
        }
        return n;
    }

    /** wear away exposed blocks: those with at least `faces` open sides vanish at `ratio` % chance */
    static int roughen(EditSession es, BlockPos t, int r, String shape, int faces, int ratio, Random rnd, Pattern mask) {
        World w = es.world;
        List<BlockPos> hit = new ArrayList<>();
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            if (!inShape(shape, x, y, z, r)) continue;
            BlockPos p = t.add(x, y, z);
            if (!solid(w, p)) continue;
            int open = 0;
            for (EnumFacing f : EnumFacing.values()) if (!solid(w, p.offset(f))) open++;
            if (open >= faces && rnd.nextInt(100) < ratio) hit.add(p);
        }
        int n = 0;
        for (BlockPos p : hit) { if (!put(es, p, AIR, mask)) return n; n++; }
        return n;
    }

    /** Voronoi cracks: blocks near the border between two cells become air (or the active block) */
    static int shatter(EditSession es, BlockPos t, int r, String shape, double cell, int width, long seed, Pattern fill, Pattern mask) {
        int n = 0;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            if (!inShape(shape, x, y, z, r)) continue;
            BlockPos p = t.add(x, y, z);
            if (!solid(es.world, p)) continue;
            double[] d = voronoi(p.getX() / cell, p.getY() / cell, p.getZ() / cell, seed);
            if ((d[1] - d[0]) * cell > width * 0.5) continue;
            if (!put(es, p, fill == null ? AIR : fill.next(), mask)) return n;
            n++;
        }
        return n;
    }

    /** distances to the nearest and second-nearest jittered cell point */
    private static double[] voronoi(double x, double y, double z, long seed) {
        int cx = MathHelper.floor(x), cy = MathHelper.floor(y), cz = MathHelper.floor(z);
        double d1 = 9e9, d2 = 9e9;
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) for (int k = -1; k <= 1; k++) {
            long h = seed ^ ((cx + i) * 73856093L) ^ ((cy + j) * 19349663L) ^ ((cz + k) * 83492791L);
            Random r = new Random(h);
            double px = cx + i + r.nextDouble(), py = cy + j + r.nextDouble(), pz = cz + k + r.nextDouble();
            double d = Math.sqrt((px - x) * (px - x) + (py - y) * (py - y) + (pz - z) * (pz - z));
            if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) d2 = d;
        }
        return new double[]{d1, d2};
    }

    /**
     * Extrude the connected face you clicked: every block of the same kind on that face (within the brush radius)
     * grows `count` layers outward (Ctrl: shrink by removing the face layer).
     */
    static int extrude(EditSession es, BlockPos t, EnumFacing face, int r, int count, boolean shrink, String compare) {
        World w = es.world;
        IBlockState like = w.getBlockState(t);
        boolean anySolid = "solid".equals(compare);
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        List<BlockPos> faceBlocks = new ArrayList<>();
        q.add(t);
        seen.add(t);
        int limit = Math.max(64, (2 * r + 1) * (2 * r + 1));
        while (!q.isEmpty() && faceBlocks.size() < limit) {
            BlockPos p = q.poll();
            IBlockState s = w.getBlockState(p);
            boolean same = anySolid ? solid(w, p) : s == like;
            if (!same || solid(w, p.offset(face))) continue;
            if (p.distanceSq(t) > (r + 0.5) * (r + 0.5)) continue;
            faceBlocks.add(p);
            for (EnumFacing f : EnumFacing.values()) {
                if (f.getAxis() == face.getAxis()) continue;
                BlockPos nb = p.offset(f);
                if (seen.add(nb)) q.add(nb);
            }
        }
        int n = 0;
        for (BlockPos p : faceBlocks) {
            if (shrink) { if (es.set(p, AIR)) n++; continue; }
            IBlockState s = w.getBlockState(p);
            for (int i = 1; i <= count; i++) { if (solid(w, p.offset(face, i))) break; if (es.set(p.offset(face, i), s)) n++; }
        }
        return n;
    }

    // ---------------------------------------------------------------- fluid

    /** fill the connected air from the clicked spot (sideways and down; up too if allowed) */
    static int flood(EditSession es, BlockPos start, int limit, Pattern pat, boolean up) {
        World w = es.world;
        if (solid(w, start)) return 0;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        int n = 0;
        limit = MathHelper.clamp(limit, 1, 500_000);
        while (!q.isEmpty() && n < limit) {
            BlockPos p = q.poll();
            IBlockState s = w.getBlockState(p);
            if (!s.getBlock().isAir(s, w, p) && !s.getBlock().isReplaceable(w, p)) continue;
            if (!es.set(p, pat.next())) return n;
            n++;
            for (EnumFacing f : EnumFacing.values()) {
                if (f == EnumFacing.UP && !up) continue;
                BlockPos nb = p.offset(f);
                if (nb.getY() < 1 || nb.getY() > 255 || nb.distanceSq(start) > 256 * 256) continue;
                if (seen.add(nb)) q.add(nb);
            }
        }
        return n;
    }

    /** pour water (or the active fluid/block) into the low parts of the brush: fills air resting on something, up to the clicked height */
    static int pour(EditSession es, BlockPos t, int r, Pattern pat) {
        World w = es.world;
        int n = 0;
        for (int y = -r; y <= 0; y++) for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            if (x * x + y * y + z * z > (r + 0.5) * (r + 0.5)) continue;
            BlockPos p = t.add(x, y + 1, z);
            if (solid(w, p) || !w.isAirBlock(p)) continue;
            BlockPos below = p.down();
            boolean resting = solid(w, below) || es.positions().contains(below);
            if (!resting) continue;
            if (es.set(p, pat.next())) n++;
        }
        return n;
    }
}
