package com.flightring;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Mob effects added by this mod, registered on the mod event bus from the
 * {@link FlightRingMod} constructor exactly like {@link ModDataComponents}.
 * <p>
 * There is one effect so far, {@link #ETERNAL_SOUL_FIRE}: the infernal relic ring's
 * "Flame Lord" brands its victims with soul fire that never goes out
 * (see {@link EternalSoulFireEffect} and {@link FlameLordAbility}).
 */
public class ModMobEffects {

    /** The registry of this mod's mob effects. */
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, FlightRingMod.MODID);

    /**
     * "Eternal Soul Fire" - registered as {@code simpleflightring:eternal_soul_fire}.
     * A {@link net.minecraft.world.effect.MobEffectCategory#BENEFICIAL} effect on purpose:
     * a good effect is never put out like a flame and never treated as a debuff by other
     * mods' "remove harmful effects" logic, while the vanilla heart particles it still
     * shows (soul fire blue) stay the same. Its behaviour lives in
     * {@link EternalSoulFireEffect}.
     */
    public static final DeferredHolder<MobEffect, MobEffect> ETERNAL_SOUL_FIRE =
            EFFECTS.register("eternal_soul_fire", EternalSoulFireEffect::new);

    private ModMobEffects() {
    }
}
