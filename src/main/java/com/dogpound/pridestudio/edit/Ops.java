package com.dogpound.pridestudio.edit;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** Runs a tool from the Pride Studio panel, wand, brush or /ps for a player, with undo. Creative or operator only. */
public final class Ops {
    private Ops() {}

    static final String NEED_SEL = "§cPick two corners first (Selection page, the wand, or [ and ]).";

    public static String run(EntityPlayerMP p, String op, NBTTagCompound a) {
        if (!p.isCreative() && !p.canUseCommand(2, "pridestudio")) return "§cPride Studio needs creative mode or operator rights.";
        UUID u = p.getUniqueID();
        BlockPos feet = new BlockPos(p);
        BlockPos o = a.hasKey("x") ? new BlockPos(a.getInteger("x"), a.getInteger("y"), a.getInteger("z")) : feet;
        if (o.distanceSq(feet) > 600 * 600) return "§cThat spot is too far away.";
        int r = clamp(a.hasKey("radius") ? a.getInteger("radius") : 5, 0, 256), d = clamp(a.hasKey("depth") ? a.getInteger("depth") : 1, 1, 256);
        int size = clamp(a.hasKey("size") ? a.getInteger("size") : 5, 1, 128), h = clamp(a.hasKey("height") ? a.getInteger("height") : 5, 1, 256);
        int amount = clamp(a.hasKey("amount") ? a.getInteger("amount") : 1, 1, 256), thick = clamp(a.hasKey("thick") ? a.getInteger("thick") : 1, 1, 64);
        boolean hollow = a.getBoolean("hollow"), skipAir = a.getBoolean("skipAir");
        String block = a.getString("block").isEmpty() ? "stone" : a.getString("block");
        Pattern mask = a.getString("mask").isEmpty() || !a.getBoolean("useMask") ? null : Pattern.parse(a.getString("mask"));
        try {
            switch (op) {
                // ---------------------------------------------------------------- history
                case "undo": { int k = Math.max(1, a.getInteger("times")), done = 0; EditSession last = null; for (int i = 0; i < k; i++) { EditSession s = History.undo(u); if (s == null) break; last = s; done++; } return last == null ? "Nothing to undo." : "↶ Undid " + (done > 1 ? done + " edits" : last.label + " (" + last.size() + " blocks)"); }
                case "redo": { int k = Math.max(1, a.getInteger("times")), done = 0; EditSession last = null; for (int i = 0; i < k; i++) { EditSession s = History.redo(u); if (s == null) break; last = s; done++; } return last == null ? "Nothing to redo." : "↷ Redid " + (done > 1 ? done + " edits" : last.label + " (" + last.size() + " blocks)"); }
                case "clearhist": History.clear(u); return "History cleared.";
                case "sync": return "";
                case "panel": return "#open:panel";
                case "menu": return "#open:menu";
                case "editor": return "#open:editor";
                case "magicselect": {
                    // every connected block like the clicked one (6 directions + optional corners), up to the limit -> its bounding box
                    net.minecraft.block.state.IBlockState like = p.world.getBlockState(o);
                    if (like.getBlock() == net.minecraft.init.Blocks.AIR) return "§cClick a block, not air.";
                    boolean exact = a.getBoolean("exact"), corners = a.getBoolean("corners");
                    int limit = clamp(a.hasKey("limit") ? a.getInteger("limit") : 100000, 1, 1_000_000), cnt = 0;
                    java.util.Set<BlockPos> seen = new java.util.HashSet<>();
                    java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
                    q.add(o); seen.add(o);
                    int x0 = o.getX(), y0 = o.getY(), z0 = o.getZ(), x1 = x0, y1 = y0, z1 = z0;
                    while (!q.isEmpty() && cnt < limit) {
                        BlockPos c = q.poll();
                        net.minecraft.block.state.IBlockState s2 = p.world.getBlockState(c);
                        if (exact ? s2 != like : s2.getBlock() != like.getBlock()) continue;
                        cnt++;
                        x0 = Math.min(x0, c.getX()); y0 = Math.min(y0, c.getY()); z0 = Math.min(z0, c.getZ());
                        x1 = Math.max(x1, c.getX()); y1 = Math.max(y1, c.getY()); z1 = Math.max(z1, c.getZ());
                        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                            int m = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                            if (m == 0 || (!corners && m > 1)) continue;
                            BlockPos nb = c.add(dx, dy, dz);
                            if (nb.getY() < 0 || nb.getY() > 255 || nb.distanceSq(o) > 512 * 512) continue;
                            if (seen.add(nb)) q.add(nb);
                        }
                    }
                    Selections.set(u, new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1));
                    return "✦ Magic select: " + cnt + " connected " + like.getBlock().getLocalizedName() + (cnt >= limit ? " (limit reached)" : "") + sizeMsg(u);
                }
                case "give": {
                    ItemStack st = new ItemStack(a.getString("item").equals("brush") ? com.dogpound.pridestudio.StudioItems.BRUSH : com.dogpound.pridestudio.StudioItems.WAND);
                    if (!p.inventory.addItemStackToInventory(st)) p.dropItem(st, false);
                    return "✦ Here's your " + (a.getString("item").equals("brush") ? "Studio Brush (right click paints, left click reverses)" : "Studio Wand (left click = corner 1, right click = corner 2)");
                }
                // ---------------------------------------------------------------- selection
                case "pos1": Selections.POS1.put(u, o); return "Corner 1 at " + str(o) + sizeMsg(u);
                case "pos2": Selections.POS2.put(u, o); return "Corner 2 at " + str(o) + sizeMsg(u);
                case "selclear": Selections.POS1.remove(u); Selections.POS2.remove(u); return "Selection cleared.";
                case "selchunk": { int cx = o.getX() >> 4 << 4, cz = o.getZ() >> 4 << 4; Selections.set(u, new BlockPos(cx, 0, cz), new BlockPos(cx + 15, 255, cz + 15)); return "Selected this chunk" + sizeMsg(u); }
                case "expand": case "contract": case "shift": case "fullheight": {
                    if (!Selections.has(u)) return NEED_SEL;
                    BlockPos mn = Selections.min(u), mx = Selections.max(u);
                    if (op.equals("fullheight")) { Selections.set(u, new BlockPos(mn.getX(), 0, mn.getZ()), new BlockPos(mx.getX(), 255, mx.getZ())); return "Selection now goes from bedrock to the sky" + sizeMsg(u); }
                    String ds = a.getString("dir");
                    EnumFacing[] dirs = ds.equals("all") ? EnumFacing.values() : ds.equals("sides") ? EnumFacing.HORIZONTALS : new EnumFacing[]{ dir(p, ds) };
                    for (EnumFacing f : dirs) {
                        int sgn = op.equals("contract") ? -1 : 1;
                        boolean pos = f.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE;
                        if (op.equals("shift")) { mn = mn.offset(f, amount); mx = mx.offset(f, amount); }
                        else if (pos) mx = mx.offset(f, sgn * amount);
                        else mn = mn.offset(f, sgn * amount);
                    }
                    if (mn.getX() > mx.getX() || mn.getY() > mx.getY() || mn.getZ() > mx.getZ()) return "§cThat would shrink the selection to nothing.";
                    Selections.set(u, mn, mx);
                    return (op.equals("expand") ? "Expanded" : op.equals("contract") ? "Shrunk" : "Moved") + " the selection" + sizeMsg(u);
                }
                case "count": { if (!Selections.has(u)) return NEED_SEL; Pattern m = a.getString("mask").isEmpty() ? null : Pattern.parse(a.getString("mask")); return "✦ " + RegionOps.count(p.world, Selections.min(u), Selections.max(u), m) + (m == null ? " solid blocks" : " matching blocks") + " in the selection"; }
                case "setbiome": if (!Selections.has(u)) return NEED_SEL; return RegionOps.setBiome(p.world, Selections.min(u), Selections.max(u), a.getString("biome"));
                case "butcher": return "✦ Removed " + NatureOps.butcher(p.world, o, r, a.getBoolean("all")) + " mobs";
                case "removeitems": return "✦ Removed " + NatureOps.removeItems(p.world, o, r) + " dropped items and XP orbs";
                case "copy": case "cut": {
                    if (!Selections.has(u)) return NEED_SEL;
                    int n = ClipOps.copy(u, p.world, Selections.min(u), Selections.max(u), feet);
                    if (op.equals("copy")) return "✦ Copied " + n + " blocks (paste happens relative to where you stand now)";
                    break;
                }
                case "rotate": { Selections.Clip c = Selections.clip(u); if (c == null) return "§cThe clipboard is empty."; ClipOps.rotate(c, a.getInteger("deg")); return "Clipboard turned " + a.getInteger("deg") + "°"; }
                case "flip": { Selections.Clip c = Selections.clip(u); if (c == null) return "§cThe clipboard is empty."; char ax = a.getString("axis").isEmpty() ? lookAxis(p) : a.getString("axis").charAt(0); ClipOps.flip(c, ax); return "Clipboard flipped along " + ax; }
                case "clipclear": Selections.CLIP.remove(u); return "Clipboard emptied.";
                case "setprop": return BlockOps.setProp(p, o, a.getString("prop"), a.getString("value"));
                default: break;
            }
            EditSession es = new EditSession(p.world, label(op, a));
            int n;
            BlockPos mn = Selections.has(u) ? Selections.min(u) : null, mx = Selections.has(u) ? Selections.max(u) : null;
            switch (op) {
                // ---------------------------------------------------------------- fill & water (WorldEdit's //fill family)
                case "fill": n = WaterOps.fill(es, o, Pattern.parse(block), r, d, false); break;
                case "fillr": n = WaterOps.fill(es, o, Pattern.parse(block), r, d, true); break;
                case "drain": n = WaterOps.drain(es, o, r); break;
                case "fixwater": n = WaterOps.fixLiquid(es, o, r, false); break;
                case "fixlava": n = WaterOps.fixLiquid(es, o, r, true); break;
                case "removenear": n = WaterOps.removeNear(es, o, Pattern.parse(a.getString("mask").isEmpty() ? block : a.getString("mask")), size); break;
                case "removeabove": n = WaterOps.removeColumn(es, o, size, h, true); break;
                case "removebelow": n = WaterOps.removeColumn(es, o, size, h, false); break;
                case "replacenear": n = WaterOps.replaceNear(es, o, Pattern.parse(a.getString("mask").isEmpty() ? "stone" : a.getString("mask")), Pattern.parse(block), size); break;
                case "snow": n = WaterOps.snow(es, o, r, false); break;
                case "thaw": n = WaterOps.snow(es, o, r, true); break;
                case "green": n = WaterOps.green(es, o, r); break;
                case "ex": n = WaterOps.extinguish(es, o, r) + NatureOps.extinguishPlayers(p.world, o, r); break;
                // ---------------------------------------------------------------- region
                case "set": case "replace": case "walls": case "outline": case "hollow": case "overlay": case "naturalize": case "smooth":
                case "stack": case "move": case "line": case "center": case "drainsel": case "clear":
                    if (mn == null) return NEED_SEL;
                    switch (op) {
                        case "set": n = RegionOps.set(es, mn, mx, Pattern.parse(block), mask); break;
                        case "replace": n = RegionOps.set(es, mn, mx, Pattern.parse(block), Pattern.parse(a.getString("mask").isEmpty() ? "stone" : a.getString("mask"))); break;
                        case "clear": n = RegionOps.set(es, mn, mx, Pattern.parse("air"), mask); break;
                        case "walls": n = RegionOps.walls(es, mn, mx, Pattern.parse(block), false); break;
                        case "outline": n = RegionOps.walls(es, mn, mx, Pattern.parse(block), true); break;
                        case "hollow": n = RegionOps.hollow(es, mn, mx, thick, null); break;
                        case "overlay": n = RegionOps.overlay(es, mn, mx, Pattern.parse(block), d); break;
                        case "naturalize": n = RegionOps.naturalize(es, mn, mx); break;
                        case "smooth": n = RegionOps.smooth(es, mn, mx, amount); break;
                        case "stack": n = RegionOps.stack(es, mn, mx, dir(p, a.getString("dir")), amount, skipAir); break;
                        case "move": {
                            EnumFacing f = dir(p, a.getString("dir"));
                            n = RegionOps.move(es, mn, mx, f, amount, null, skipAir);
                            if (a.getBoolean("moveSel")) Selections.set(u, mn.offset(f, amount), mx.offset(f, amount));
                            break;
                        }
                        case "line": n = RegionOps.line(es, Selections.pos1(u), Selections.pos2(u), Pattern.parse(block), thick); break;
                        case "center": n = RegionOps.center(es, mn, mx, Pattern.parse(block)); break;
                        default: n = RegionOps.drainBox(es, mn, mx); break;
                    }
                    break;
                case "cut": n = RegionOps.set(es, mn, mx, Pattern.parse("air"), null); break;
                case "paste": {
                    Selections.Clip c = Selections.clip(u);
                    if (c == null) return "§cThe clipboard is empty — copy something first.";
                    n = ClipOps.paste(es, c, o, skipAir);
                    break;
                }
                // ---------------------------------------------------------------- builder capabilities (one block, far away)
                case "place": n = BlockOps.place(es, p, o); break;
                case "breakone": n = es.set(o, net.minecraft.init.Blocks.AIR.getDefaultState()) ? 1 : 0; break;
                case "replaceone": n = BlockOps.replace(es, p, o); break;
                // ---------------------------------------------------------------- shapes
                case "shape": n = ShapeOps.run(es, a.getString("shape"), o, Pattern.parse(block), r, h, thick, amount, hollow); break;
                // ---------------------------------------------------------------- nature
                case "forest": n = NatureOps.forest(es, o, r, clamp(a.getInteger("density"), 1, 100), a.getString("tree").isEmpty() ? "oak" : a.getString("tree")); break;
                case "flora": n = NatureOps.flora(es, o, r, clamp(a.getInteger("density"), 1, 100)); break;
                case "smoothnear": n = RegionOps.smooth(es, o.add(-r, -r, -r), o.add(r, r, r), amount); break;
                case "naturalizenear": n = RegionOps.naturalize(es, o.add(-r, -r, -r), o.add(r, r, r)); break;
                // ---------------------------------------------------------------- brush
                case "brush": {
                    EnumFacing face = a.hasKey("face") ? EnumFacing.getFront(a.getInteger("face")) : EnumFacing.UP;
                    if (SculptOps.has(a.getString("brush"))) {
                        es.label = a.getString("brush");
                        n = SculptOps.run(es, a.getString("brush"), o, face, a, Pattern.parse(block), mask, a.getBoolean("alt"));
                        break;
                    }
                    Vec3d eye = p.getPositionEyes(1F);
                    n = BrushOps.run(es, a.getString("brush"), o, face, new BlockPos(eye), Pattern.parse(block), mask, clamp(size, 0, 64), h,
                            clamp(a.getInteger("density"), 1, 100), clamp(a.getInteger("strength"), 1, 16), a.getBoolean("alt"), a.getString("tree"));
                    break;
                }
                default: return "§cUnknown tool: " + op;
            }
            if (op.equals("place") || op.equals("breakone") || op.equals("replaceone")) { History.push(u, es); return ""; }   // quiet: no chat spam while building
            if (op.equals("brush") && a.hasKey("stroke")) { History.pushStroke(u, es, a.getInteger("stroke")); return ""; }  // editor mode: quiet, one undo per drag
            History.push(u, es);
            if (op.equals("cut")) return "✦ Cut " + n + " blocks to the clipboard";
            return n == 0 ? "Nothing to change here." : "✦ " + es.label + ": " + (op.equals("forest") ? n + " trees (" + es.size() + " blocks)" : n + " blocks") + (es.size() >= EditSession.LIMIT ? " (stopped at the limit)" : "");
        } catch (IllegalArgumentException e) {
            return "§c" + e.getMessage();
        }
    }

    /** the facing for "up/down/north/…", or the way the player looks for "look"/"" */
    static EnumFacing dir(EntityPlayerMP p, String d) {
        EnumFacing f = d == null || d.isEmpty() || d.equals("look") ? null : EnumFacing.byName(d);
        if (f != null) return f;
        Vec3d v = p.getLookVec();
        return EnumFacing.getFacingFromVector((float) v.x, (float) v.y, (float) v.z);
    }

    static char lookAxis(EntityPlayerMP p) { EnumFacing f = dir(p, "look"); return f.getAxis() == EnumFacing.Axis.X ? 'x' : f.getAxis() == EnumFacing.Axis.Y ? 'y' : 'z'; }

    static String str(BlockPos b) { return b.getX() + ", " + b.getY() + ", " + b.getZ(); }

    static String sizeMsg(UUID u) {
        if (!Selections.has(u)) return "";
        BlockPos a = Selections.min(u), b = Selections.max(u);
        long x = b.getX() - a.getX() + 1, y = b.getY() - a.getY() + 1, z = b.getZ() - a.getZ() + 1;
        return "  §7(" + x + "×" + y + "×" + z + " = " + (x * y * z) + " blocks)";
    }

    private static String label(String op, NBTTagCompound a) {
        String b = a.getString("block");
        switch (op) {
            case "fill": return "Fill " + b;
            case "fillr": return "Deep fill " + b;
            case "replacenear": case "replace": return "Replace " + a.getString("mask") + " → " + b;
            case "shape": return (a.getBoolean("hollow") ? "Hollow " : "") + a.getString("shape") + " of " + b;
            case "brush": return "Brush " + a.getString("brush") + (a.getBoolean("alt") ? " (reverse)" : "");
            case "fixwater": return "Fix water";
            case "fixlava": return "Fix lava";
            case "removenear": return "Remove near";
            case "removeabove": return "Remove above";
            case "removebelow": return "Remove below";
            case "ex": return "Put out fires";
            case "drainsel": return "Drain selection";
            case "smoothnear": return "Smooth nearby";
            case "naturalizenear": return "Naturalize nearby";
            case "set": return "Set " + b;
            default: return Character.toUpperCase(op.charAt(0)) + op.substring(1);
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
