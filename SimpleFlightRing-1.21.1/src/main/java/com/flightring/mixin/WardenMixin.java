package com.flightring.mixin;

import com.flightring.SculkSoulAbility;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "Sculk Soul" effect 2 - wardens see the wearer as one of their own.
 * <p>
 * A warden never goes through {@code Mob#setTarget}: it keeps its victim in the brain
 * memory {@code ATTACK_TARGET} ({@code Warden#setAttackTarget}), which means NeoForge's
 * {@code LivingChangeTargetEvent} never fires for it. Every path that could pick a victim -
 * anger management, hearing a disturbance, the melee and sonic boom behaviours - funnels
 * through {@code Warden#canTargetEntity} instead, so denying that one call for the ring
 * wearer makes the warden ignore them completely.
 * <p>
 * The wearer still gets a fight if they start one: attacking a warden is remembered by
 * {@link SculkSoulAbility#wardenMayTarget}, which lets that warden target them again until
 * the wearer dies or leaves its follow range.
 */
@Mixin(Warden.class)
public abstract class WardenMixin {

    @Inject(method = "canTargetEntity", at = @At("HEAD"), cancellable = true)
    private void simpleflightring$sculkSoulNeutrality(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!SculkSoulAbility.wardenMayTarget((Warden) (Object) this, target)) {
            cir.setReturnValue(false);
        }
    }
}
