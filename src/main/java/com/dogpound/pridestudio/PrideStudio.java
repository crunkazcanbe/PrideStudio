package com.dogpound.pridestudio;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/**
 * Pride Studio — our own "Blender in Minecraft" world editor (a favourite idea, 2026-10-04): Axiom-style editor mode,
 * VoxelSniper-style brushes, WorldEdit's best tools (a favourite: //fill), and the orthographic build camera.
 * v0.1: the camera. Everything is our own code; feature ideas credited in README.
 */
@Mod(modid = PrideStudio.MODID, name = "Pride Studio", version = "0.6.0", acceptableRemoteVersions = "*")
public class PrideStudio {
    public static final String MODID = "pridestudio";

    @Mod.EventHandler
    public void preInit(net.minecraftforge.fml.common.event.FMLPreInitializationEvent e) {
        com.dogpound.pridestudio.net.StudioNet.init();
    }

    @Mod.EventHandler
    public void serverStart(net.minecraftforge.fml.common.event.FMLServerStartingEvent e) {
        e.registerServerCommand(new PsCommand());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        if (FMLCommonHandler.instance().getSide().isClient()) {
            OrthoCam.register();
            com.dogpound.pridestudio.client.StudioClient.register();
            ClientCommandHandler.instance.registerCommand(new CommandBase() {
                @Override public String getName() { return "pridestudio"; }
                @Override public String getUsage(ICommandSender s) { return "/pridestudio [camera]"; }
                @Override public int getRequiredPermissionLevel() { return 0; }
                @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }
                @Override public void execute(MinecraftServer server, ICommandSender sender, String[] a) {
                    Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().displayGuiScreen(a.length > 0 && a[0].equals("camera")
                            ? new OrthoCam.Screen(null) : new com.dogpound.pridestudio.client.StudioScreen(null)));
                }
            });
        }
    }
}
