package com.flightring.mixin;

import com.flightring.RaidPlunderAbility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The raid ring's wearer passes for one of the illagers, so an illager must not back away from
 * them.
 * <p>
 * The evoker is the one that cares: vanilla gives it
 * {@code AvoidEntityGoal<>(this, Player.class, 8.0F, 0.6, 1.0)} at priority 2, so an evoker that
 * no longer attacks the wearer simply runs from them and can never be reached to trade for a
 * totem. The decision is taken on the goal's own finding ({@code toAvoid}), so every other
 * avoidance - a villager running from a zombie, a mob keeping its distance from anything else -
 * stays exactly as vanilla wrote it.
 */
@Mixin(AvoidEntityGoal.class)
public abstract class IllagerAvoidMixin {

    @Shadow
    @Final
    protected PathfinderMob mob;

    @Shadow
    protected LivingEntity toAvoid;

    @Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
    private void simpleflightring$doNotFleeTheRaidWearer(CallbackInfoReturnable<Boolean> cir) {
        if (isAvoidingTheWearer()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true)
    private void simpleflightring$stopFleeingTheRaidWearer(CallbackInfoReturnable<Boolean> cir) {
        if (isAvoidingTheWearer()) {
            cir.setReturnValue(false);
        }
    }

    /** True while this illager's avoidance is aimed at a player wearing a usable raid ring. */
    private boolean isAvoidingTheWearer() {
        return this.mob instanceof Raider
                && this.toAvoid instanceof Player player
                && RaidPlunderAbility.isWearer(player);
    }
}
