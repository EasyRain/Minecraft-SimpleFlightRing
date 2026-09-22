package com.flightring;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.common.PercentageAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own attributes - exactly one so far.
 * <p>
 * {@link #DODGE_CHANCE} is the relic rings' dodge for the case where ApothicAttributes is NOT
 * installed. That mod owns the very same concept ({@code apothic_attributes:dodge_chance}), so
 * with it installed our values are handed to ITS attribute instead (see
 * {@link ApothicAttributesCompat}) and this one stays at zero. Having our own is what makes
 * Curios list the dodge on the ring's tooltip in both cases, and what
 * {@link RelicDodgeHandler} rolls when the mythic mod is missing.
 * <p>
 * A registered attribute only exists on an entity if that entity's attribute supplier has it,
 * so it must also be added to the player (see {@link #onEntityAttributeModification}).
 */
public final class ModAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, FlightRingMod.MODID);

    /**
     * "Dodge Chance", registered as {@code simpleflightring:dodge_chance}: a NeoForge
     * {@code PercentageAttribute} (0..1, so +0.25 reads as "+25%") with exactly the range the
     * mythic one uses, so the two are interchangeable. Its translation key is the plain
     * attribute id (like every non-vanilla attribute), see the lang files.
     */
    public static final DeferredHolder<Attribute, Attribute> DODGE_CHANCE = ATTRIBUTES.register(
            "dodge_chance",
            () -> new PercentageAttribute("simpleflightring:dodge_chance", 0.0D, 0.0D, 1.0D).setSyncable(true));

    private ModAttributes() {
    }

    /** Attributes have to be part of an entity type's supplier to be applied to it. */
    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, DODGE_CHANCE);
    }
}
