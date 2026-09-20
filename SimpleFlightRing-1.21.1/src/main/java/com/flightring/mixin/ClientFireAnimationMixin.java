package com.flightring.mixin;

import com.flightring.FlameLordPassives;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The flame lord does not look like they are burning. Living in lava with an infernal ring on
 * should not wrap the wearer in flames - the ring already grants Fire Resistance, and the burning
 * animation is what makes lava unreadable. Client side only: the burning animation is drawn by the
 * renderer from {@link Entity#displayFireAnimation()}, and the local player's Curios slot is
 * readable here without a packet.
 * <p>
 * Only the local player can be checked this way - another player's Curios slot is not synced to
 * this client, so their flames stay visible to onlookers.
 */
@Mixin(Entity.class)
public abstract class ClientFireAnimationMixin {

    @Inject(method = "displayFireAnimation", at = @At("HEAD"), cancellable = true)
    private void simpleflightring$noBurningAnimation(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && !FlameLordPassives.wornRing(player).isEmpty()) {
            cir.setReturnValue(false);
        }
    }
}
