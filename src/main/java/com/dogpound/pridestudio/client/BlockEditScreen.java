package com.dogpound.pridestudio.client;

import com.dogpound.pridestudio.PrideFrame;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Edit Block: every property of the block you're looking at (facing, half, colour, age…) with ‹ › to change it. */
public class BlockEditScreen extends GuiScreen {
    private final GuiScreen parent;
    private final BlockPos pos;
    private final List<Object[]> hits = new ArrayList<>();

    public BlockEditScreen(GuiScreen parent) {
        this.parent = parent;
        RayTraceResult r = ClientState.look();
        pos = r == null ? null : r.getBlockPos();
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void drawScreen(int mx, int my, float pt) {
        hits.clear();
        PrideFrame f = PrideFrame.sized(width, height, 420, 320);
        IBlockState st = pos == null ? null : mc.world.getBlockState(pos);
        f.draw(this, "Edit Block", pos == null ? "" : "§7" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        int y = f.cy + 4;
        if (st == null) { fontRenderer.drawStringWithShadow("§cLook at a block first (within your Reach).", f.cx, y, 0xFFFFFF); }
        else {
            fontRenderer.drawStringWithShadow("§d" + st.getBlock().getLocalizedName() + " §8" + st.getBlock().getRegistryName(), f.cx, y, 0xFFFFFF);
            y += 16;
            if (st.getPropertyKeys().isEmpty()) fontRenderer.drawStringWithShadow("§7This block has nothing to change.", f.cx, y, 0xFFFFFF);
            for (IProperty p : st.getPropertyKeys()) {
                List<Comparable> vals = new ArrayList<>(p.getAllowedValues());
                Comparable cur = st.getValue(p);
                int i = vals.indexOf(cur);
                PrideFrame.button(f.cx, y, 18, 16, "‹", PrideFrame.BUTTON, mx, my);
                PrideFrame.button(f.cx + 22, y, 18, 16, "›", PrideFrame.BUTTON, mx, my);
                fontRenderer.drawStringWithShadow(p.getName() + ": §f" + p.getName(cur), f.cx + 48, y + 4, 0xF5A9B8);
                String prev = p.getName(vals.get((i - 1 + vals.size()) % vals.size())), next = p.getName(vals.get((i + 1) % vals.size()));
                hits.add(new Object[]{ f.cx, y, 18, 16, p.getName(), prev });
                hits.add(new Object[]{ f.cx + 22, y, 18, 16, p.getName(), next });
                y += 20;
            }
        }
        int by = f.y + f.h - 24;
        PrideFrame.button(f.cx + f.cw - 80, by, 80, 18, "✔ Done", PrideFrame.BUTTON, mx, my);
        hits.add(new Object[]{ f.cx + f.cw - 80, by, 80, 18, null, null });
        super.drawScreen(mx, my, pt);
    }

    @Override
    protected void mouseClicked(int mx, int my, int b) throws IOException {
        for (Object[] h : hits) {
            if (mx < (Integer) h[0] || my < (Integer) h[1] || mx >= (Integer) h[0] + (Integer) h[2] || my >= (Integer) h[1] + (Integer) h[3]) continue;
            if (h[4] == null) { mc.displayGuiScreen(parent instanceof BuilderMenu ? null : parent); return; }
            NBTTagCompound t = new NBTTagCompound();
            ClientState.at(t, pos);
            t.setString("prop", (String) h[4]); t.setString("value", (String) h[5]);
            ClientState.send("setprop", t);
            return;
        }
    }
}
