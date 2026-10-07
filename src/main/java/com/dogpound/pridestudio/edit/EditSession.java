package com.dogpound.pridestudio.edit;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One edit (one button press): every block it changes is remembered (block + its machine/chest data), so Undo puts the
 * world back exactly. Redo replays the new blocks. Server side only.
 */
public final class EditSession {
    public final World world;
    public String label;
    final Map<BlockPos, IBlockState> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
    final Map<BlockPos, NBTTagCompound> beforeTe = new LinkedHashMap<>();
    public static final int LIMIT = 2_000_000;                // most blocks one edit may change

    /** a dry run (the client's live preview): records where blocks WOULD go, never touches the world */
    public final boolean dry;
    public static final int PREVIEW_LIMIT = 40_000;

    public EditSession(World world, String label) { this(world, label, false); }
    public EditSession(World world, String label, boolean dry) { this.world = world; this.label = label; this.dry = dry; }

    public java.util.Set<BlockPos> positions() { return after.keySet(); }

    public int size() { return after.size(); }

    /** Fold a later edit into this one (one brush stroke = one undo): keep our "before", take its "after". */
    public void absorb(EditSession o) {
        for (Map.Entry<BlockPos, IBlockState> e : o.before.entrySet()) before.putIfAbsent(e.getKey(), e.getValue());
        for (Map.Entry<BlockPos, NBTTagCompound> e : o.beforeTe.entrySet()) beforeTe.putIfAbsent(e.getKey(), e.getValue());
        after.putAll(o.after);
    }

    /** change one block (remembering what was there); false if the edit is too big */
    public boolean set(BlockPos pos, IBlockState state) {
        if (pos.getY() < 0 || pos.getY() > 255) return true;
        if (dry) { after.put(pos.toImmutable(), state); return after.size() < PREVIEW_LIMIT; }
        IBlockState old = world.getBlockState(pos);
        if (old == state) return true;
        if (after.size() >= LIMIT) return false;
        BlockPos p = pos.toImmutable();
        if (!before.containsKey(p)) {
            before.put(p, old);
            TileEntity te = world.getTileEntity(p);
            if (te != null) try { beforeTe.put(p, te.writeToNBT(new NBTTagCompound())); } catch (Throwable ignored) { }
        }
        after.put(p, state);
        world.setBlockState(p, state, 2);
        return true;
    }

    private Map<BlockPos, IBlockState> snap;

    /** for world-gen helpers that place blocks themselves (trees): remember the box first, then record what changed */
    public void beginCapture(BlockPos min, BlockPos max) {
        snap = new java.util.HashMap<>();
        for (BlockPos p : BlockPos.getAllInBox(min, max)) if (p.getY() >= 0 && p.getY() < 256) snap.put(p, world.getBlockState(p));
    }

    public void endCapture() {
        if (snap == null) return;
        for (Map.Entry<BlockPos, IBlockState> e : snap.entrySet()) {
            IBlockState now = world.getBlockState(e.getKey());
            if (now != e.getValue()) { before.putIfAbsent(e.getKey(), e.getValue()); after.put(e.getKey(), now); }
        }
        snap = null;
    }

    /** copy a machine/chest's data onto the block just placed at `to` */
    public void copyTe(NBTTagCompound nbt, BlockPos to) {
        if (nbt == null) return;
        TileEntity te = world.getTileEntity(to);
        if (te == null) return;
        NBTTagCompound t = nbt.copy();
        t.setInteger("x", to.getX()); t.setInteger("y", to.getY()); t.setInteger("z", to.getZ());
        try { te.readFromNBT(t); te.markDirty(); } catch (Throwable ignored) { }
    }

    public void undo() {
        for (Map.Entry<BlockPos, IBlockState> e : before.entrySet()) {
            world.setBlockState(e.getKey(), e.getValue(), 2);
            NBTTagCompound t = beforeTe.get(e.getKey());
            if (t != null) {
                TileEntity te = world.getTileEntity(e.getKey());
                if (te != null) try { te.readFromNBT(t); te.markDirty(); } catch (Throwable ignored) { }
            }
        }
    }

    public void redo() {
        for (Map.Entry<BlockPos, IBlockState> e : after.entrySet()) world.setBlockState(e.getKey(), e.getValue(), 2);
    }
}
