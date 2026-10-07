package com.dogpound.pridestudio.mixin;

import com.dogpound.pridestudio.OrthoCam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pride Camera: the world (and clouds) drawn with an orthographic projection while it's on (see OrthoCam). */
@Mixin(value = EntityRenderer.class, remap = false)
public abstract class MixinOrthoCamera {
    @Redirect(method = { "func_78479_a", "func_180437_a", "func_175068_a" }, require = 0, remap = false,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V"))
    private void pridestudio$ortho(float fov, float aspect, float near, float far) {
        if (!OrthoCam.on()) { Project.gluPerspective(fov, aspect, near, far); return; }
        float h = OrthoCam.zoom(Minecraft.getMinecraft().getRenderPartialTicks()), w = h * aspect;
        GL11.glOrtho(-w, w, -h, h, OrthoCam.S.near, OrthoCam.S.far);
    }

    @Inject(method = "func_78476_b", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void pridestudio$noHand(float pt, int pass, CallbackInfo ci) {
        if (OrthoCam.on() && OrthoCam.S.hideHand) ci.cancel();
    }
}
