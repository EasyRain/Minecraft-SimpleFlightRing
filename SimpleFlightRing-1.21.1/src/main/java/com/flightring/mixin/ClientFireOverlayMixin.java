package com.flightring.mixin;

import com.flightring.FlameLordPassives;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The first person burning overlay, gone for an infernal ring wearer: standing in lava with the
 * ring on should not paint the screen with flames. Only that one overlay is skipped - the
 * underwater, lava and block overlays are left exactly as they are.
 * <p>
 * Client side only, and only for the local player (the Curios slot is read locally, no packet).
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ClientFireOverlayMixin {

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    // renderFire is a static method in both game versions, so the handler has to be static too.
    private static void simpleflightring$noBurningOverlay(CallbackInfo cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && !FlameLordPassives.wornRing(player).isEmpty()) {
            cir.cancel();
        }
    }
}
