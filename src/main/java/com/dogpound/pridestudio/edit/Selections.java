package com.dogpound.pridestudio.edit;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Each player's two corners and clipboard (server side, forgotten on restart like WorldEdit's). */
public final class Selections {
    private Selections() {}
    static final Map<UUID, BlockPos> POS1 = new HashMap<>(), POS2 = new HashMap<>();
    static final Map<UUID, Clip> CLIP = new HashMap<>();

    public static final class Clip {
        public final List<BlockPos> rel = new ArrayList<>();
        public final List<IBlockState> states = new ArrayList<>();
        public final List<NBTTagCompound> tes = new ArrayList<>();
    }

    public static BlockPos pos1(UUID u) { return POS1.get(u); }
    public static BlockPos pos2(UUID u) { return POS2.get(u); }
    public static Clip clip(UUID u) { return CLIP.get(u); }

    public static BlockPos min(UUID u) {
        BlockPos a = POS1.get(u), b = POS2.get(u);
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public static BlockPos max(UUID u) {
        BlockPos a = POS1.get(u), b = POS2.get(u);
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public static boolean has(UUID u) { return POS1.containsKey(u) && POS2.containsKey(u); }

    static void set(UUID u, BlockPos min, BlockPos max) {
        POS1.put(u, new BlockPos(min.getX(), clampY(min.getY()), min.getZ()));
        POS2.put(u, new BlockPos(max.getX(), clampY(max.getY()), max.getZ()));
    }

    static int clampY(int y) { return Math.max(0, Math.min(255, y)); }
}
