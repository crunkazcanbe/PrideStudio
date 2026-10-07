package com.dogpound.pridestudio.edit;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * VoxelSniper-style brushes for the Studio Brush: right click paints, left click does the opposite.
 * Ideas from VoxelSniper (GPL; nothing copied), written fresh for 1.12.2.
 */
public final class BrushOps {
    private BrushOps() {}
    private static final Random RND = new Random();
    static final IBlockState AIR = Blocks.AIR.getDefaultState();

    public static final String[] TYPES = {"ball", "disc", "cylinder", "cube", "snipe", "splatter", "paint", "overlay", "blend", "erode",
            "fillin", "raise", "lower", "flatten", "filldown", "drain", "snow", "tree", "jagged", "line"};

    static boolean air(World w, BlockPos p) { return RegionOps.air(w, p); }

    public static int run(EditSession es, String type, BlockPos t, EnumFacing face, BlockPos eye, Pattern pat, Pattern mask, int r, int h, int density, int strength, boolean alt, String treeType) {
        World w = es.world;
        double R2 = (r + 0.5) * (r + 0.5);
        int n = 0;
        switch (type) {
            case "ball": case "cube": case "disc": case "cylinder": case "splatter": case "paint": {
                int ylo = type.equals("disc") ? 0 : type.equals("cylinder") ? 0 : -r, yhi = type.equals("disc") ? 0 : type.equals("cylinder") ? h - 1 : r;
                BlockPos c = t;
                for (int x = -r; x <= r; x++) for (int y = ylo; y <= yhi; y++) for (int z = -r; z <= r; z++) {
                    boolean in = type.equals("cube") || (type.equals("disc") || type.equals("cylinder") ? x * x + z * z <= R2 : x * x + y * y + z * z <= R2);
                    if (!in) continue;
                    if (type.equals("splatter") && RND.nextInt(100) >= density) continue;
                    BlockPos p = c.add(x, y, z);
                    if (type.equals("paint") && air(w, p)) continue;               // paint only recolours what's there
                    if (!RegionOps.put(es, p, alt ? AIR : pat.next(), mask)) return n;
                    n++;
                }
                return n;
            }
            case "snipe": {
                BlockPos p = alt ? t : t.offset(face == null ? EnumFacing.UP : face);
                return es.set(p, alt ? AIR : pat.next()) ? 1 : 0;
            }
            case "overlay": {
                // the top `depth` blocks of every column in the circle (alt: put a layer on top instead)
                for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > R2) continue;
                    BlockPos g = NatureOps.ground(w, t.getX() + x, t.getY(), t.getZ() + z);
                    if (g == null) continue;
                    if (alt) { if (es.set(g.up(), pat.next())) n++; continue; }
                    for (int d = 0; d < Math.max(1, h); d++) if (!air(w, g.down(d)) && RegionOps.put(es, g.down(d), pat.next(), mask)) n++;
                }
                return n;
            }
            case "blend": case "erode": case "fillin": {
                // look at a snapshot, then decide each block by its neighbours
                Map<BlockPos, IBlockState> snap = new HashMap<>();
                for (BlockPos p : BlockPos.getAllInBox(t.add(-r - 1, -r - 1, -r - 1), t.add(r + 1, r + 1, r + 1))) snap.put(p, w.getBlockState(p));
                for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > R2) continue;
                    BlockPos p = t.add(x, y, z);
                    IBlockState me = snap.get(p);
                    boolean meAir = me.getBlock() == Blocks.AIR || me.getMaterial().isLiquid();
                    Map<IBlockState, Integer> votes = new HashMap<>();
                    int solid = 0, total = 0;
                    for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        if (type.equals("blend") ? false : Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) continue;   // erode/fill: 6 faces
                        IBlockState s = snap.get(p.add(dx, dy, dz));
                        total++;
                        if (s.getBlock() != Blocks.AIR && !s.getMaterial().isLiquid()) { solid++; votes.merge(s, 1, Integer::sum); }
                    }
                    IBlockState common = null; int best = 0;
                    for (Map.Entry<IBlockState, Integer> e : votes.entrySet()) if (e.getValue() > best) { best = e.getValue(); common = e.getKey(); }
                    int need = Math.max(1, Math.min(6, strength));
                    boolean erode = type.equals("erode") ? !alt : type.equals("fillin") && alt;
                    if (type.equals("blend")) {
                        boolean wantSolid = solid * 2 > total;
                        if (alt) { if (!wantSolid && !meAir && es.set(p, AIR)) n++; }          // alt: only smooth away bumps
                        else if (wantSolid && meAir && common != null) { if (es.set(p, common)) n++; }
                        else if (!wantSolid && !meAir && es.set(p, AIR)) n++;
                    } else if (erode) {
                        if (!meAir && total - solid >= need && es.set(p, AIR)) n++;          // exposed blocks wear away
                    } else {
                        if (meAir && solid >= need && common != null && es.set(p, common)) n++; // nooks fill in
                    }
                }
                return n;
            }
            case "raise": case "lower": {
                boolean up = type.equals("raise") != alt;
                for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d > r + .5) continue;
                    int amt = (int) Math.round(strength * (1 - d / (r + 1)) + 0.4);
                    if (amt <= 0) continue;
                    BlockPos g = NatureOps.ground(w, t.getX() + x, t.getY(), t.getZ() + z);
                    if (g == null) continue;
                    IBlockState top = w.getBlockState(g), under = w.getBlockState(g.down());
                    if (!under.getMaterial().blocksMovement()) under = top;
                    if (up) {
                        for (int i = 0; i < amt; i++) if (es.set(g.up(i), under)) n++;
                        if (es.set(g.up(amt), top)) n++;
                    } else {
                        for (int i = 0; i < amt; i++) if (es.set(g.down(i), AIR)) n++;
                        if (es.set(g.down(amt), top)) n++;
                    }
                }
                return n;
            }
            case "flatten": {
                // everything above the clicked height goes, everything below up to it gets filled (alt: only cut)
                for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > R2) continue;
                    for (int y = 1; y <= h; y++) { BlockPos p = t.add(x, y, z); if (!air(w, p) && es.set(p, AIR)) n++; }
                    if (alt) continue;
                    for (int y = 0; y >= -h; y--) { BlockPos p = t.add(x, y, z); if (air(w, p) || w.getBlockState(p).getMaterial().isLiquid()) { if (es.set(p, pat.next())) n++; } }
                }
                return n;
            }
            case "filldown": {
                for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > R2) continue;
                    for (int y = alt ? 0 : 1; t.getY() + y > 0; y--) {
                        BlockPos p = t.add(x, y, z);
                        if (!air(w, p) && !w.getBlockState(p).getMaterial().isLiquid()) { if (y <= 0) break; else continue; }
                        if (es.set(p, pat.next())) n++;
                    }
                }
                return n;
            }
            case "drain": return WaterOps.drain(es, t.up(), r);
            case "snow": return WaterOps.snow(es, t.up(), r, alt);
            case "tree": {
                es.beginCapture(t.add(-12, -2, -12), t.add(12, 40, 12));
                if (NatureOps.tree(treeType == null || treeType.isEmpty() ? "mixed" : treeType).generate(w, RND, t.up())) n = 1;
                es.endCapture();
                return n;
            }
            case "jagged": {
                // a rough spike: random walk of balls from the target outward, like a crystal or rock
                BlockPos c = t;
                EnumFacing dir = face == null ? EnumFacing.UP : face;
                for (int i = 0; i < Math.max(2, h); i++) {
                    int rr = Math.max(0, r - i * r / Math.max(2, h));
                    for (int x = -rr; x <= rr; x++) for (int y = -rr; y <= rr; y++) for (int z = -rr; z <= rr; z++)
                        if (x * x + y * y + z * z <= (rr + .5) * (rr + .5) && RegionOps.put(es, c.add(x, y, z), alt ? AIR : pat.next(), mask)) n++;
                    c = c.offset(dir).add(RND.nextInt(3) - 1, 0, RND.nextInt(3) - 1);
                }
                return n;
            }
            case "line": {
                // from your eyes to the clicked block, `r` thick
                if (eye == null) return 0;
                return RegionOps.line(es, eye, t, alt ? Pattern.parse("air") : pat, Math.max(1, r));
            }
            default: throw new IllegalArgumentException("Unknown brush: " + type);
        }
    }
}
