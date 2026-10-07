package com.dogpound.pridestudio.net;

import com.dogpound.pridestudio.edit.History;
import com.dogpound.pridestudio.edit.Ops;
import com.dogpound.pridestudio.edit.Selections;
import net.minecraft.util.math.BlockPos;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;


/** Panel button -> server runs the tool on its own thread -> the result line and history come back to the panel. */
public final class StudioNet {
    private StudioNet() {}
    public static final SimpleNetworkWrapper NET = NetworkRegistry.INSTANCE.newSimpleChannel("pridestudio");

    public static void init() {
        NET.registerMessage(OpHandler.class, Op.class, 0, Side.SERVER);
        NET.registerMessage(ReplyHandler.class, Reply.class, 1, Side.CLIENT);
    }

    public static final class Op implements IMessage {
        public String op = ""; public NBTTagCompound args = new NBTTagCompound();
        public Op() {}
        public Op(String op, NBTTagCompound args) { this.op = op; this.args = args; }
        @Override public void fromBytes(ByteBuf b) { op = ByteBufUtils.readUTF8String(b); args = ByteBufUtils.readTag(b); if (args == null) args = new NBTTagCompound(); }
        @Override public void toBytes(ByteBuf b) { ByteBufUtils.writeUTF8String(b, op); ByteBufUtils.writeTag(b, args); }
    }

    /** the result line plus everything the panel shows: history, redo count, corners, clipboard size */
    public static final class Reply implements IMessage {
        public String text = ""; public NBTTagCompound state = new NBTTagCompound();
        public Reply() {}
        public Reply(String t, NBTTagCompound s) { text = t; state = s; }
        @Override public void fromBytes(ByteBuf b) { text = ByteBufUtils.readUTF8String(b); state = ByteBufUtils.readTag(b); if (state == null) state = new NBTTagCompound(); }
        @Override public void toBytes(ByteBuf b) { ByteBufUtils.writeUTF8String(b, text); ByteBufUtils.writeTag(b, state); }
    }

    public static void push(EntityPlayerMP p, String text) { NET.sendTo(new Reply(text, state(p)), p); }

    static NBTTagCompound state(EntityPlayerMP p) {
        java.util.UUID u = p.getUniqueID();
        NBTTagCompound t = new NBTTagCompound();
        net.minecraft.nbt.NBTTagList h = new net.minecraft.nbt.NBTTagList();
        for (String s : History.labels(u)) h.appendTag(new net.minecraft.nbt.NBTTagString(s));
        t.setTag("history", h);
        t.setInteger("redo", History.redoCount(u));
        BlockPos a = Selections.pos1(u), b = Selections.pos2(u);
        if (a != null) t.setIntArray("pos1", new int[]{a.getX(), a.getY(), a.getZ()});
        if (b != null) t.setIntArray("pos2", new int[]{b.getX(), b.getY(), b.getZ()});
        Selections.Clip c = Selections.clip(u);
        t.setInteger("clip", c == null ? 0 : c.rel.size());
        return t;
    }

    public static final class OpHandler implements IMessageHandler<Op, IMessage> {
        @Override public IMessage onMessage(Op m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> {
                String r = Ops.run(p, m.op, m.args);
                NET.sendTo(new Reply(r, state(p)), p);
            });
            return null;
        }
    }

    public static final class ReplyHandler implements IMessageHandler<Reply, IMessage> {
        @Override public IMessage onMessage(Reply m, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> com.dogpound.pridestudio.client.ClientState.reply(m.text, m.state));
            return null;
        }
    }
}
