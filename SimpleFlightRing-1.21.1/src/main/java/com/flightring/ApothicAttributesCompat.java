package com.flightring;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.fml.ModList;

/**
 * Optional bridge to ApothicAttributes (the "神话" attribute mod).
 * <p>
 * That mod owns the game's dodge system: it registers {@code apothic_attributes:dodge_chance}
 * (a NeoForge {@code PercentageAttribute}, so +0.25 means "+25%") and rolls it in its own
 * handler. Rather than adding a second, competing dodge roll, our relic rings simply contribute
 * their values to that attribute - one roll, its sound and particles, and the numbers show up in
 * its attribute screen. When the mod is absent, {@link RelicDodgeHandler} rolls the very same
 * rules on its own.
 * <p>
 * The attribute is looked up <b>by id at runtime</b>, so this mod keeps zero compile-time and
 * zero runtime dependency on ApothicAttributes (the same approach as {@link AllthemodiumCompat}).
 */
public final class ApothicAttributesCompat {

    /** Id of their dodge attribute. */
    private static final ResourceLocation DODGE_CHANCE =
            ResourceLocation.fromNamespaceAndPath("apothic_attributes", "dodge_chance");

    private static boolean resolved = false;
    private static Holder<Attribute> dodgeAttribute = null;

    private ApothicAttributesCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("apothic_attributes");
    }

    /**
     * Their dodge attribute, or {@code null} when the mod - or just that attribute - is missing.
     * Resolved once and cached; attributes are registered long before any ring is worn.
     */
    public static Holder<Attribute> dodgeChance() {
        if (!resolved) {
            resolved = true;
            dodgeAttribute = BuiltInRegistries.ATTRIBUTE.getHolder(DODGE_CHANCE).orElse(null);
        }
        return dodgeAttribute;
    }

    /**
     * Whether the dodge roll belongs to that mod (i.e. our own handler must stay quiet).
     * Its attribute being present is what matters, not the mod being on the mod list.
     */
    public static boolean handlesDodge() {
        return dodgeChance() != null;
    }
}
