package com.flightring;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Attribute bonuses of one relic ring (see {@link RelicRing}).
 * <p>
 * They are granted ONLY while that ring is worn in the Curios "flight ring" slot: Curios
 * collects them through {@code ICurio#getAttributeModifiers} (see {@link CuriosCompat}) and
 * applies them to the wearer's attributes, exactly like it does for the AllTheModium chain
 * ({@link RingBonuses}). A relic ring carried in the inventory gives nothing but flight time.
 * <p>
 * Amounts follow the vanilla attribute conventions:
 * <ul>
 *   <li>flat amounts use {@code ADD_VALUE} - armour, toughness, attack damage, max health,
 *       both interaction ranges, luck and dodge chance;</li>
 *   <li>the three percentage fields ({@code attackDamagePercent}, {@code attackSpeedPercent},
 *       {@code movementSpeedPercent}) hold the number the user reads off the tooltip - 10 means
 *       "+10%" - and are divided by 100 on the way into {@code ADD_MULTIPLIED_BASE}.</li>
 * </ul>
 * {@code dodgeChance} is a fraction (0.25 = 25%). It is NOT a vanilla attribute: when
 * ApothicAttributes is installed the value is handed to that mod's {@code dodge_chance}
 * attribute and rolled by its handler, otherwise {@link RelicDodgeHandler} rolls it with the
 * very same rules (see {@link #attributeModifiers(Holder)}).
 */
public record RelicBonuses(double armor, double armorToughness, double attackDamage, double attackDamagePercent,
                           double attackSpeedPercent, double movementSpeedPercent, double maxHealth,
                           double blockRange, double entityRange, double luck, double dodgeChance) {

    private static final String NAMESPACE = FlightRingMod.MODID;

    /**
     * The bonuses the user assigned to each relic ring, themed around what the ring is about:
     * sculk/infernal/raid are the fighters, miner works, emerald/ocean/desert endure,
     * ocean/desert/ender evade.
     */
    public static RelicBonuses of(RelicRing relic) {
        return switch (relic) {
            case SCULK -> builder()
                    .attackDamage(20).attackSpeedPercent(10).blockRange(2).entityRange(2)
                    .build();
            case MINER -> builder()
                    .blockRange(5).armor(2).armorToughness(2)
                    .build();
            case EMERALD -> builder()
                    .entityRange(2).armor(5).armorToughness(5).maxHealth(10)
                    .build();
            case OCEAN -> builder()
                    .movementSpeedPercent(10).dodgeChance(0.25).maxHealth(10)
                    .build();
            case DESERT -> builder()
                    .maxHealth(30).armor(10).armorToughness(10).dodgeChance(0.50).luck(10)
                    .build();
            case RAID -> builder()
                    .attackDamagePercent(25).attackSpeedPercent(10).armor(4).armorToughness(4).entityRange(2)
                    .build();
            case INFERNAL -> builder()
                    .attackDamage(20).attackSpeedPercent(20).entityRange(2).armor(8).armorToughness(4)
                    .build();
            case ENDER -> builder()
                    .blockRange(10).entityRange(2).armor(3).armorToughness(3).dodgeChance(0.20)
                    .build();
        };
    }

    /** Whether this ring dodges at all (only then is the dodge machinery worth touching). */
    public boolean hasDodge() {
        return dodgeChance > 0.0;
    }

    /**
     * The modifiers Curios hands to the wearer for this ring.
     *
     * @param dodgeAttribute ApothicAttributes' {@code dodge_chance} attribute when that mod is
     *                       installed (see {@link ApothicAttributesCompat}), {@code null}
     *                       otherwise. With it, our dodge values live in that mod's attribute and
     *                       its handler rolls them; without it the value is ignored here and
     *                       {@link RelicDodgeHandler} rolls it instead.
     */
    public Multimap<Holder<Attribute>, AttributeModifier> attributeModifiers(Holder<Attribute> dodgeAttribute) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = LinkedHashMultimap.create();
        put(modifiers, Attributes.ARMOR, "armor", armor, AttributeModifier.Operation.ADD_VALUE);
        put(modifiers, Attributes.ARMOR_TOUGHNESS, "armor_toughness", armorToughness,
                AttributeModifier.Operation.ADD_VALUE);
        put(modifiers, Attributes.ATTACK_DAMAGE, "attack_damage", attackDamage,
                AttributeModifier.Operation.ADD_VALUE);
        put(modifiers, Attributes.ATTACK_DAMAGE, "attack_damage_percent", attackDamagePercent / 100.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        put(modifiers, Attributes.ATTACK_SPEED, "attack_speed_percent", attackSpeedPercent / 100.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        put(modifiers, Attributes.MOVEMENT_SPEED, "movement_speed_percent", movementSpeedPercent / 100.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        put(modifiers, Attributes.MAX_HEALTH, "max_health", maxHealth,
                AttributeModifier.Operation.ADD_VALUE);
        put(modifiers, Attributes.BLOCK_INTERACTION_RANGE, "block_interaction_range", blockRange,
                AttributeModifier.Operation.ADD_VALUE);
        put(modifiers, Attributes.ENTITY_INTERACTION_RANGE, "entity_interaction_range", entityRange,
                AttributeModifier.Operation.ADD_VALUE);
        put(modifiers, Attributes.LUCK, "luck", luck, AttributeModifier.Operation.ADD_VALUE);
        if (dodgeAttribute != null) {
            put(modifiers, dodgeAttribute, "dodge_chance", dodgeChance, AttributeModifier.Operation.ADD_VALUE);
        }
        return modifiers;
    }

    /** Adds one modifier, skipping the values this ring does not grant. */
    private static void put(Multimap<Holder<Attribute>, AttributeModifier> modifiers, Holder<Attribute> attribute,
                            String name, double amount, AttributeModifier.Operation operation) {
        if (amount != 0.0) {
            modifiers.put(attribute, new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath(NAMESPACE, "relic_" + name), amount, operation));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder: eleven positional constructor arguments would be unreadable. */
    public static final class Builder {

        private double armor;
        private double armorToughness;
        private double attackDamage;
        private double attackDamagePercent;
        private double attackSpeedPercent;
        private double movementSpeedPercent;
        private double maxHealth;
        private double blockRange;
        private double entityRange;
        private double luck;
        private double dodgeChance;

        public Builder armor(double value) {
            this.armor = value;
            return this;
        }

        public Builder armorToughness(double value) {
            this.armorToughness = value;
            return this;
        }

        public Builder attackDamage(double value) {
            this.attackDamage = value;
            return this;
        }

        public Builder attackDamagePercent(double value) {
            this.attackDamagePercent = value;
            return this;
        }

        public Builder attackSpeedPercent(double value) {
            this.attackSpeedPercent = value;
            return this;
        }

        public Builder movementSpeedPercent(double value) {
            this.movementSpeedPercent = value;
            return this;
        }

        public Builder maxHealth(double value) {
            this.maxHealth = value;
            return this;
        }

        public Builder blockRange(double value) {
            this.blockRange = value;
            return this;
        }

        public Builder entityRange(double value) {
            this.entityRange = value;
            return this;
        }

        public Builder luck(double value) {
            this.luck = value;
            return this;
        }

        public Builder dodgeChance(double value) {
            this.dodgeChance = value;
            return this;
        }

        public RelicBonuses build() {
            return new RelicBonuses(armor, armorToughness, attackDamage, attackDamagePercent, attackSpeedPercent,
                    movementSpeedPercent, maxHealth, blockRange, entityRange, luck, dodgeChance);
        }
    }
}
