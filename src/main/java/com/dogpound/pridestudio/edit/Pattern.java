package com.dogpound.pridestudio.edit;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What to place: "stone", "minecraft:wool:3", "stone,dirt" (even mix) or "70%stone,30%cobblestone" (weighted).
 * Same idea as WorldEdit/VoxelSniper patterns, written fresh.
 */
public final class Pattern {
    private final List<IBlockState> states = new ArrayList<>();
    private final List<Double> weights = new ArrayList<>();
    private double total;
    private final Random rnd = new Random();

    public static Pattern parse(String text) {
        Pattern p = new Pattern();
        for (String part : text.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            double w = 1;
            int pct = part.indexOf('%');
            if (pct > 0) { w = Double.parseDouble(part.substring(0, pct)); part = part.substring(pct + 1); }
            IBlockState s = state(part);
            if (s == null) throw new IllegalArgumentException("Unknown block: " + part);
            p.states.add(s); p.weights.add(w); p.total += w;
        }
        if (p.states.isEmpty()) throw new IllegalArgumentException("No blocks given");
        return p;
    }

    /** "stone", "minecraft:stone", "wool:3", "minecraft:wool:3" */
    @SuppressWarnings("deprecation")
    public static IBlockState state(String s) {
        s = s.trim().toLowerCase(java.util.Locale.ROOT);
        int meta = 0;
        String[] parts = s.split(":");
        String id;
        if (parts.length == 3) { id = parts[0] + ":" + parts[1]; meta = Integer.parseInt(parts[2]); }
        else if (parts.length == 2 && parts[1].matches("\\d+")) { id = parts[0]; meta = Integer.parseInt(parts[1]); }
        else id = s;
        if (id.equals("air")) return Blocks.AIR.getDefaultState();
        Block b = Block.REGISTRY.getObject(new ResourceLocation(id));
        if (b == Blocks.AIR && !id.endsWith("air")) return null;
        return b.getStateFromMeta(meta);
    }

    public IBlockState next() {
        if (states.size() == 1) return states.get(0);
        double r = rnd.nextDouble() * total;
        for (int i = 0; i < states.size(); i++) { r -= weights.get(i); if (r <= 0) return states.get(i); }
        return states.get(states.size() - 1);
    }

    public boolean contains(IBlockState s) {
        for (IBlockState x : states) if (x.getBlock() == s.getBlock()) return true;
        return false;
    }
}
