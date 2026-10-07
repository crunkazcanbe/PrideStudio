package com.dogpound.pridestudio.edit;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/** One-block tools for the builder capabilities (far reach, angel placement, replace mode) and Edit Block. */
public final class BlockOps {
    private BlockOps() {}

    /** the block the player holds, as a block state (null if it isn't a block) */
    static IBlockState held(EntityPlayerMP p) {
        ItemStack s = p.getHeldItemMainhand();
        if (!(s.getItem() instanceof ItemBlock)) return null;
        Block b = ((ItemBlock) s.getItem()).getBlock();
        try { return b.getStateFromMeta(s.getItem().getMetadata(s.getMetadata())); } catch (Throwable t) { return b.getDefaultState(); }
    }

    public static int place(EditSession es, EntityPlayerMP p, BlockPos o) {
        IBlockState s = held(p);
        if (s == null || !es.world.getBlockState(o).getBlock().isReplaceable(es.world, o)) return 0;
        return es.set(o, s) ? 1 : 0;
    }

    public static int replace(EditSession es, EntityPlayerMP p, BlockPos o) {
        IBlockState s = held(p);
        return s != null && es.set(o, s) ? 1 : 0;
    }

    /** change one property of a block (Edit Block screen): facing, half, color, age … */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String setProp(EntityPlayerMP p, BlockPos o, String name, String value) {
        IBlockState st = p.world.getBlockState(o);
        for (IProperty prop : st.getPropertyKeys()) {
            if (!prop.getName().equals(name)) continue;
            com.google.common.base.Optional v = prop.parseValue(value);
            if (!v.isPresent()) return "§c" + value + " isn't a value of " + name;
            EditSession es = new EditSession(p.world, "Edit block " + name + "=" + value);
            es.set(o, st.withProperty(prop, (Comparable) v.get()));
            History.push(p.getUniqueID(), es);
            return "✦ " + name + " = " + value;
        }
        return "§cThat block has no " + name;
    }
}
