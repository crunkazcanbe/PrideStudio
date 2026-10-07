package com.dogpound.pridestudio.client;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Saved hotbars for the builder menu: endless rows of 9, 8 rows a page (config/pridestudio-hotbars.dat). */
public final class Hotbars {
    private Hotbars() {}
    public static final int ROWS = 8;
    public static final List<ItemStack[]> ROWS_LIST = new ArrayList<>();
    private static final File FILE = new File("config/pridestudio-hotbars.dat");
    private static boolean loaded;

    public static ItemStack[] row(int i) {
        load();
        while (ROWS_LIST.size() <= i) { ItemStack[] r = new ItemStack[9]; java.util.Arrays.fill(r, ItemStack.EMPTY); ROWS_LIST.add(r); }
        return ROWS_LIST.get(i);
    }

    public static int pages() {
        load();
        int last = -1;
        for (int i = 0; i < ROWS_LIST.size(); i++) for (ItemStack s : ROWS_LIST.get(i)) if (!s.isEmpty()) last = i;
        return Math.max(1, last / ROWS + 2);            // always one empty page after the last used one
    }

    /** swap a saved row with the live hotbar (creative only: the server trusts creative slot packets) */
    public static boolean swap(int i) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || !mc.player.isCreative()) { ClientState.say("§cSwapping hotbars needs creative mode."); return false; }
        ItemStack[] saved = row(i), live = new ItemStack[9];
        for (int k = 0; k < 9; k++) {
            live[k] = mc.player.inventory.getStackInSlot(k).copy();
            ItemStack put = saved[k].copy();
            mc.player.inventory.setInventorySlotContents(k, put);
            mc.playerController.sendSlotPacket(put, 36 + k);
        }
        ROWS_LIST.set(i, live);
        save();
        return true;
    }

    public static void clear() { ROWS_LIST.clear(); save(); }

    static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!FILE.isFile()) return;
            NBTTagCompound t = CompressedStreamTools.read(FILE);
            if (t == null) return;
            NBTTagList rows = t.getTagList("rows", 9);
            for (int i = 0; i < rows.tagCount(); i++) {
                NBTTagList r = (NBTTagList) rows.get(i);
                ItemStack[] a = new ItemStack[9];
                for (int k = 0; k < 9; k++) a[k] = k < r.tagCount() ? new ItemStack(r.getCompoundTagAt(k)) : ItemStack.EMPTY;
                ROWS_LIST.add(a);
            }
        } catch (Throwable ignored) { }
    }

    public static void save() {
        try {
            NBTTagCompound t = new NBTTagCompound();
            NBTTagList rows = new NBTTagList();
            for (ItemStack[] a : ROWS_LIST) {
                NBTTagList r = new NBTTagList();
                for (ItemStack s : a) r.appendTag(s.writeToNBT(new NBTTagCompound()));
                rows.appendTag(r);
            }
            t.setTag("rows", rows);
            CompressedStreamTools.write(t, FILE);
        } catch (Throwable ignored) { }
    }
}
