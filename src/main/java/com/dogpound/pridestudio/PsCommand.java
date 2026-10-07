package com.dogpound.pridestudio;

import com.dogpound.pridestudio.edit.Ops;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/** Command backup for every panel tool: /ps <tool> key=value …  e.g. /ps fill block=water radius=20 depth=6 */
public final class PsCommand extends CommandBase {
    @Override public String getName() { return "ps"; }
    @Override public String getUsage(ICommandSender s) { return "/ps <tool> [key=value ...]  e.g. /ps fill block=water radius=20 depth=6, /ps shape shape=sphere radius=8 hollow=true, /ps undo"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }   // Ops checks creative/op itself

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] a) throws CommandException {
        if (a.length == 0) throw new WrongUsageException(getUsage(sender));
        EntityPlayerMP p = getCommandSenderAsPlayer(sender);
        NBTTagCompound t = new NBTTagCompound();
        for (int i = 1; i < a.length; i++) {
            int eq = a[i].indexOf('=');
            if (eq <= 0) continue;
            String k = a[i].substring(0, eq), v = a[i].substring(eq + 1);
            if (v.matches("-?\\d+")) t.setInteger(k, Integer.parseInt(v));
            else if (v.equals("true") || v.equals("false")) t.setBoolean(k, Boolean.parseBoolean(v));
            else t.setString(k, v);
        }
        if (t.hasKey("mask")) t.setBoolean("useMask", true);
        String r = Ops.run(p, a[0], t);
        if (r.startsWith("#open:")) { com.dogpound.pridestudio.net.StudioNet.push(p, r); return; }
        if (!r.isEmpty()) p.sendMessage(new TextComponentString(r.startsWith("§") ? r : "§d" + r));
        com.dogpound.pridestudio.net.StudioNet.push(p, "");
    }
}
