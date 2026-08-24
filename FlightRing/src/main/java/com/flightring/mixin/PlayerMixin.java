package com.flightring.mixin;

import com.flightring.FlightHandler;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Vanilla divides the mining speed by 5 while the player is not on the ground
 * (which includes flying). With the Flight Stability enchantment on a carried
 * ring that slow-down is cancelled while actually flying. Changing the vanilla
 * divisor directly - instead of scaling the final speed (e.g. via an event) -
 * leaves every other speed modifier untouched, so the behaviour stays
 * compatible with other mods and vanilla enchantments that modify mining speed.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @ModifyConstant(
            method = "getDigSpeed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)F",
            constant = @Constant(floatValue = 5.0F)
    )
    private float flightring$noMidAirMiningSlowdown(float divisor) {
        Player self = (Player) (Object) this;
        if (self.getAbilities().flying && !self.onGround() && FlightHandler.hasFlightStability(self)) {
            return 1.0F;
        }
        return divisor;
    }
}
