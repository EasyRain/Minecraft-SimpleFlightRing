package com.flightring;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Attribute bonuses of a linked ring (the AllTheModium chain).
 * <p>
 * They are granted ONLY while the ring is worn in the "flight ring" Curios slot: Curios
 * collects them through {@code ICurio#getAttributeModifiers} (see {@link CuriosCompat})
 * and applies them to the wearer's attributes, exactly like vanilla equipment does for
 * armour. A ring lying in the inventory gives nothing but flight time.
 * <p>
 * {@code attackDamage} is a fraction (0.04 = +4%), the rest are flat amounts. The
 * interaction range is applied to both block and entity reach.
 */
public record RingBonuses(int armor, double armorToughness, double attackDamage, double interactionRange) {

    private static final String NAMESPACE = "simpleflightring";

    /** The modifiers handed to Curios for one equipped ring. */
    public Multimap<Holder<Attribute>, AttributeModifier> attributeModifiers() {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = LinkedHashMultimap.create();
        modifiers.put(Attributes.ARMOR, modifier("armor", armor, AttributeModifier.Operation.ADD_VALUE));
        modifiers.put(Attributes.ARMOR_TOUGHNESS,
                modifier("armor_toughness", armorToughness, AttributeModifier.Operation.ADD_VALUE));
        modifiers.put(Attributes.ATTACK_DAMAGE,
                modifier("attack_damage", attackDamage, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        modifiers.put(Attributes.BLOCK_INTERACTION_RANGE,
                modifier("block_interaction_range", interactionRange, AttributeModifier.Operation.ADD_VALUE));
        modifiers.put(Attributes.ENTITY_INTERACTION_RANGE,
                modifier("entity_interaction_range", interactionRange, AttributeModifier.Operation.ADD_VALUE));
        return modifiers;
    }

    /** Modifier ids are stable per ring bonus, so Curios can update/remove them cleanly. */
    private static AttributeModifier modifier(String name, double amount, AttributeModifier.Operation operation) {
        return new AttributeModifier(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "ring_" + name), amount, operation);
    }
}
