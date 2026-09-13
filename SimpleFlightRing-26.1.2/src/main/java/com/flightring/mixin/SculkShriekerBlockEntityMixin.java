package com.flightring.mixin;

import com.flightring.SculkSoulAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Sculk Soul" effect 3 - the wearer stays silent in the deep dark.
 * <p>
 * Cancelling sound and game events covers everything the wearer does, but stepping ONTO a
 * shrieker does not go through either: {@code SculkShriekerBlock#stepOn} calls the block
 * entity's {@code tryShriek} directly. This denies that call for a silenced wearer, so
 * walking over a shrieker no longer sets it off (or summons a warden).
 */
@Mixin(SculkShriekerBlockEntity.class)
public abstract class SculkShriekerBlockEntityMixin {

    @Inject(method = "tryShriek", at = @At("HEAD"), cancellable = true)
    private void simpleflightring$silenceSculkSoulWearer(ServerLevel level, ServerPlayer player, CallbackInfo ci) {
        if (SculkSoulAbility.isSilenced(player)) {
            ci.cancel();
        }
    }
}
