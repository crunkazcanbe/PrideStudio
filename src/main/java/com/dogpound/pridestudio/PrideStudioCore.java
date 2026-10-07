package com.dogpound.pridestudio;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Loads Pride Studio's camera patch early (Minecraft's own renderer is loaded before normal mods). */
@IFMLLoadingPlugin.Name("PrideStudio")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1003)
public class PrideStudioCore implements IFMLLoadingPlugin, IEarlyMixinLoader {
    private static final String CONFIG = "pridestudio.mixins.json";
    @Override public void injectData(Map<String, Object> data) { try { MixinBootstrap.init(); Mixins.addConfiguration(CONFIG); } catch (Throwable ignored) { } }
    @Override public List<String> getMixinConfigs() { return Arrays.asList(CONFIG); }
    @Override public String[] getASMTransformerClass() { return new String[0]; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public String getAccessTransformerClass() { return null; }
}
