package com.dogpound.pridestudio.edit;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

/** Copy / cut / paste / rotate / flip. The clipboard remembers where you stood, so paste lands the same way round. */
public final class ClipOps {
    private ClipOps() {}

    public static int copy(UUID u, World w, BlockPos a, BlockPos b, BlockPos stand) {
        Selections.Clip c = new Selections.Clip();
        for (BlockPos p : BlockPos.getAllInBox(a, b)) {
            c.rel.add(p.subtract(stand));
            c.states.add(w.getBlockState(p));
            TileEntity t = w.getTileEntity(p);
            NBTTagCompound nbt = null;
            if (t != null) try { nbt = t.writeToNBT(new NBTTagCompound()); } catch (Throwable ignored) { }
            c.tes.add(nbt);
        }
        Selections.CLIP.put(u, c);
        return c.rel.size();
    }

    public static int paste(EditSession es, Selections.Clip c, BlockPos at, boolean skipAir) {
        int n = 0;
        for (int i = 0; i < c.rel.size(); i++) {
            IBlockState s = c.states.get(i);
            if (skipAir && s.getBlock() == Blocks.AIR) continue;
            BlockPos to = at.add(c.rel.get(i));
            if (!es.set(to, s)) break;
            es.copyTe(c.tes.get(i), to);
            n++;
        }
        return n;
    }

    /** turn the clipboard clockwise by 90, 180 or 270 degrees */
    public static void rotate(Selections.Clip c, int deg) {
        int q = ((deg / 90) % 4 + 4) % 4;
        Rotation rot = q == 1 ? Rotation.CLOCKWISE_90 : q == 2 ? Rotation.CLOCKWISE_180 : q == 3 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.NONE;
        for (int i = 0; i < c.rel.size(); i++) {
            c.rel.set(i, c.rel.get(i).rotate(rot));
            c.states.set(i, c.states.get(i).withRotation(rot));
        }
    }

    /** mirror the clipboard along x, y or z */
    public static void flip(Selections.Clip c, char axis) {
        for (int i = 0; i < c.rel.size(); i++) {
            BlockPos p = c.rel.get(i);
            if (axis == 'x') { c.rel.set(i, new BlockPos(-p.getX(), p.getY(), p.getZ())); c.states.set(i, c.states.get(i).withMirror(Mirror.FRONT_BACK)); }
            else if (axis == 'z') { c.rel.set(i, new BlockPos(p.getX(), p.getY(), -p.getZ())); c.states.set(i, c.states.get(i).withMirror(Mirror.LEFT_RIGHT)); }
            else c.rel.set(i, new BlockPos(p.getX(), -p.getY(), p.getZ()));
        }
    }
}
