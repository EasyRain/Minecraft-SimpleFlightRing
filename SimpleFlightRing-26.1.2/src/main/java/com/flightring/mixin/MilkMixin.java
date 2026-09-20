package com.flightring.mixin;

import com.flightring.FlightRingMod;
import com.flightring.ModMobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.1.2 made milk data driven: a milk bucket carries a
 * {@code ClearAllStatusEffectsConsumeEffect}, and its {@code apply} is the one that calls
 * {@code LivingEntity#removeAllEffects()}.
 * <p>
 * Intercepting this consume effect rather than the sweep is what keeps the two apart: a bucket of
 * milk leaves eternal soul fire burning, while {@code /effect clear} - which calls
 * {@code removeAllEffects()} directly, without ever going through this consume effect - still
 * clears the mark the way the command should.
 * <p>
 * The instance is taken off at {@code HEAD} (so the sweep never sees it) and handed back at
 * {@code RETURN}, which also sends the client the update packet it needs. The captured instance
 * lives in a {@link ThreadLocal} because mixin classes are shared by every instance and the server
 * ticks its worlds on its own thread.
 * <p>
 * 1.21.1 needs no mixin for the same result: there a milk bucket cures the effects that carry
 * {@code EffectCures.MILK}, and {@link com.flightring.EternalSoulFireEffect} declares no cures at
 * all.
 */
@Mixin(ClearAllStatusEffectsConsumeEffect.class)
public abstract class MilkMixin {

    /** The soul fire instance taken off the entity by the milk currently being drunk. */
    @Unique
    private static final ThreadLocal<MobEffectInstance> simpleflightring$soulFire =
            new ThreadLocal<>();

    @Inject(method = "apply", at = @At("HEAD"))
    private void simpleflightring$saveSoulFire(Level level, ItemStack stack, LivingEntity user,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide()) {
            return;
        }
        MobEffectInstance soulFire = user.getEffect(ModMobEffects.ETERNAL_SOUL_FIRE);
        if (soulFire == null) {
            return;
        }
        user.removeEffect(ModMobEffects.ETERNAL_SOUL_FIRE);
        simpleflightring$soulFire.set(soulFire);
        FlightRingMod.LOGGER.debug("[FlightRing] {} tried to wash off eternal soul fire with milk",
                user.getName().getString());
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void simpleflightring$restoreSoulFire(Level level, ItemStack stack, LivingEntity user,
                                                  CallbackInfoReturnable<Boolean> cir) {
        MobEffectInstance soulFire = simpleflightring$soulFire.get();
        if (soulFire == null) {
            return;
        }
        simpleflightring$soulFire.remove();
        // The mark survives milk; only the ocean ring's blessing and a Fire Resistance potion can
        // wear it down.
        user.addEffect(new MobEffectInstance(soulFire));
    }
}
