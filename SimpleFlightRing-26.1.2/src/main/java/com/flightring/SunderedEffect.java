package com.flightring;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "破甲" (Sundered): the timer half of the 破甲 prefix.
 * <p>
 * The armour loss itself is a transient {@code ARMOR} modifier that {@link RingAffixHandler} accumulates; this
 * effect only marks how long it lasts. The cleanup deliberately does <b>not</b> live in
 * {@code removeAttributeModifiers}: vanilla calls that every time an existing effect is <b>refreshed</b> (it
 * removes and re-adds the attribute modifiers of an updated effect), which wiped the shred on every hit.
 * {@code MobEffectEvent.Remove} fires only for a real removal, so the modifier is dropped exactly once - when the
 * debuff expires or is cleansed - for mobs and players alike.
 */
public class SunderedEffect extends MobEffect {

    /** Duration of one application: 30 seconds. */
    public static final int DURATION = 600;

    /** Id of the {@code ARMOR} modifier {@link RingAffixHandler} accumulates. */
    public static final net.minecraft.resources.Identifier SHRED_ARMOUR_ID =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "shred_armour");

    public SunderedEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B5A2B);
    }
}
