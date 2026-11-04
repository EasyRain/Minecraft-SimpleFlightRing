package com.flightring;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The random affix of a flight ring taken from a broken-dimension structure chest: one
 * <b>prefix</b> (what it does) and one <b>rarity</b> (how strongly it does it).
 * <p>
 * The numbers deliberately live in the tables below and <b>not</b> on the stack: the data
 * component only carries {@code {prefix, rarity}}. That keeps the component tiny, means a
 * balance change also applies to rings players already own, and makes "keep the affix when
 * the ring is upgraded" a matter of copying the component.
 * <p>
 * Rarity only scales the prefix's numbers, it never adds extra effects (by design - see the
 * plan in {@code docs/PLAN-随机属性戒指.md}). Chest drops stop at {@link Rarity#LEGENDARY};
 * {@link Rarity#SACRED} can only be rolled at the anvil with a reforge stone.
 */
public record RingAffix(Prefix prefix, Rarity rarity) {

    public static final Codec<RingAffix> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Prefix.CODEC.fieldOf("prefix").forGetter(RingAffix::prefix),
            Rarity.CODEC.fieldOf("rarity").forGetter(RingAffix::rarity)
    ).apply(instance, RingAffix::new));

    public static final StreamCodec<io.netty.buffer.ByteBuf, RingAffix> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.map(i -> Prefix.values()[i], Prefix::ordinal),
                    RingAffix::prefix,
                    ByteBufCodecs.VAR_INT.map(i -> Rarity.values()[i], Rarity::ordinal),
                    RingAffix::rarity,
                    RingAffix::new);

    /** How a prefix's value is meant to be read. */
    public enum Kind {
        /** Flat amount (luck, dodge, ...). */
        FLAT,
        /**
         * A percentage of the <b>player's total</b> attribute value, via
         * {@code ADD_MULTIPLIED_TOTAL}: 0.09 = +9% on top of whatever the player already has, so it
         * stays meaningful next to strong modded gear. Used for armour, max health, attack damage
         * and movement speed.
         */
        PERCENT_TOTAL,
        /** A percentage of something else (reflect share, shred share, projectile hit, ...). */
        PERCENT,
        /** A mob effect on the victim: the value is the amplifier (0 = level I). */
        EFFECT,
        /** Needs dedicated code (totem save, armour / max-health shredding). */
        SPECIAL
    }

    /**
     * The 13 prefixes with their five values, {@code T1(COMMON) .. T5(SACRED)}.
     * <p>
     * The curve is super-linear on purpose ({@code x1, x2, x4, x6, x9}): the higher rarity is
     * much rarer, so it may as well be worth noticeably more.
     * <p>
     * <b>Most values are fractions of the player's own totals</b> ({@link Kind#PERCENT_TOTAL}):
     * the ring with its own attributes is the base, the affix is the multiplier - so a fine ring on
     * a well-geared character is worth much more than the same ring early on.
     * Two prefixes stay flat on purpose: {@link #AGILE} (dodge is already a percentage attribute,
     * so scaling it by the player's total would give 0 for anyone without another dodge source) and
     * {@link #FORTUNATE} (the player's luck base is 0).
     */
    public enum Prefix implements StringRepresentable {
        /** 守护: +% of the player's total armour. */
        WARDING("warding", Kind.PERCENT_TOTAL, 0.01, 0.02, 0.04, 0.06, 0.09),
        /** 荆棘: reflects a share of the ATTACKER's attack power. */
        THORNED("thorned", Kind.PERCENT, 0.05, 0.10, 0.20, 0.30, 0.45),
        /** 生命: +% of the player's total max health. */
        VITAL("vital", Kind.PERCENT_TOTAL, 0.02, 0.04, 0.08, 0.12, 0.18),
        /** 重生: one totem-like save per charge; durability cost depends on the rarity. */
        REBORN("reborn", Kind.SPECIAL, 1, 2, 3, 4, 5),
        /** 残暴: +% of the player's total melee attack damage. */
        BRUTAL("brutal", Kind.PERCENT_TOTAL, 0.03, 0.06, 0.12, 0.18, 0.27),
        /** 精准: +% of the projectile hit's damage (see the event hook TODO). */
        PRECISE("precise", Kind.PERCENT, 0.03, 0.06, 0.12, 0.18, 0.27),
        /** 迅捷: +% of the player's total movement speed. */
        SWIFT("swift", Kind.PERCENT_TOTAL, 0.02, 0.04, 0.08, 0.12, 0.18),
        /** 灵活: flat dodge chance (dodge is already a percentage attribute). */
        AGILE("agile", Kind.FLAT, 0.02, 0.04, 0.08, 0.12, 0.18),
        /** 幸运: flat luck (the player's luck base is 0). */
        FORTUNATE("fortunate", Kind.FLAT, 0.5, 1, 2, 3, 4.5),
        /** 凋零: wither on hit (amplifier; the duration table is TODO). */
        WITHERING("withering", Kind.EFFECT, 0, 0, 1, 1, 2),
        /** 荼毒: poison on hit. */
        VENOMOUS("venomous", Kind.EFFECT, 0, 0, 1, 1, 2),
        /** 破甲: shreds a share of the target's CURRENT armour per full-charge hit. */
        SUNDERING("sundering", Kind.PERCENT, 0.02, 0.04, 0.08, 0.12, 0.18),
        /** 脆弱: deals damage proportional to the target's CURRENT health per full-charge hit. */
        BRITTLE("brittle", Kind.PERCENT, 0.02, 0.04, 0.08, 0.12, 0.18);

        public static final Codec<Prefix> CODEC = StringRepresentable.fromEnum(Prefix::values);

        private final String key;
        private final Kind kind;
        private final double[] values;

        Prefix(String key, Kind kind, double... values) {
            this.key = key;
            this.kind = kind;
            this.values = values;
        }

        @Override
        public String getSerializedName() {
            return key;
        }

        public String key() {
            return key;
        }

        public Kind kind() {
            return kind;
        }

        /** The value of this prefix at the given rarity. */
        public double value(Rarity rarity) {
            return values[rarity.ordinal()];
        }

        /** Looks a prefix up by its serialized key, or {@code null} if there is none. */
        public static Prefix byKey(String key) {
            for (Prefix prefix : values()) {
                if (prefix.key.equalsIgnoreCase(key)) {
                    return prefix;
                }
            }
            return null;
        }

        /** Display name, e.g. 「守护」/ "Warding". */
        public MutableComponent displayName() {
            return Component.translatable("ring_prefix.simpleflightring." + key);
        }

        /**
         * The tooltip line describing what this prefix does at this rarity, e.g.
         * 「守护：护甲 +4」. Uses the lang key {@code ring_prefix.simpleflightring.<key>.unit}
         * with the (already formatted) value in it.
         */
        public MutableComponent describe(Rarity rarity) {
            double value = value(rarity);
            String formatted;
            if (kind == Kind.EFFECT) {
                formatted = Long.toString((long) value + 1);
            } else if (kind == Kind.PERCENT || kind == Kind.PERCENT_TOTAL) {
                formatted = Math.round(value * 100.0) + "%";
            } else {
                formatted = value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
            }
            return Component.translatable("ring_prefix.simpleflightring." + key + ".unit", formatted);
        }
    }

    /**
     * The five rarities. Weights are the roll weights from the plan: chest drops use
     * {@code chestWeight} (SACRED is 0 there), the anvil uses {@code anvilWeight}.
     */
    public enum Rarity implements StringRepresentable {
        COMMON("common", 57, 57, 0xFFFFFF),
        RARE("rare", 25, 25, 0x55FF55),
        EPIC("epic", 10, 10, 0x5555FF),
        LEGENDARY("legendary", 5, 5, 0xAA00AA),
        SACRED("sacred", 0, 2, 0xFF5555);

        public static final Codec<Rarity> CODEC = StringRepresentable.fromEnum(Rarity::values);

        private final String key;
        private final int chestWeight;
        private final int anvilWeight;
        private final int color;

        Rarity(String key, int chestWeight, int anvilWeight, int color) {
            this.key = key;
            this.chestWeight = chestWeight;
            this.anvilWeight = anvilWeight;
            this.color = color;
        }

        @Override
        public String getSerializedName() {
            return key;
        }

        public String key() {
            return key;
        }

        /** Roll weight for a structure chest drop (sACRED is never rolled there). */
        public int chestWeight() {
            return chestWeight;
        }

        /** Roll weight for an anvil reforge. */
        public int anvilWeight() {
            return anvilWeight;
        }

        /** Name colour: white, green, blue, purple, red. */
        public int color() {
            return color;
        }

        /** Looks a rarity up by its serialized key, or {@code null} if there is none. */
        public static Rarity byKey(String key) {
            for (Rarity rarity : values()) {
                if (rarity.key.equalsIgnoreCase(key)) {
                    return rarity;
                }
            }
            return null;
        }

        /**
         * The vanilla rarity this tier is reported as. Vanilla only has four tiers, so the two
         * top ones share {@code EPIC}; the point is that mods which draw a rarity border or sort
         * by rarity (Apotheosis, JEI/EMI, ...) see a real value instead of always COMMON.
         * Reads go through {@code ItemStackRarityMixin}.
         */
        public net.minecraft.world.item.Rarity vanillaRarity() {
            return switch (this) {
                case COMMON -> net.minecraft.world.item.Rarity.COMMON;
                case RARE -> net.minecraft.world.item.Rarity.UNCOMMON;
                case EPIC -> net.minecraft.world.item.Rarity.RARE;
                case LEGENDARY, SACRED -> net.minecraft.world.item.Rarity.EPIC;
            };
        }

        /** Display name, e.g. 「神圣」/ "Sacred". */
        public MutableComponent displayName() {
            return Component.translatable("ring_rarity.simpleflightring." + key);
        }
    }

    /** Convenience: the affix's display name without the ring's own name. */
    public MutableComponent displayName() {
        MutableComponent head = prefix.displayName()
                .append(Component.literal(" "))
                .append(rarity.displayName());
        return head.withStyle(style -> style.withColor(rarity.color()));
    }

    /**
     * The full item name: 「守护的普通 金飞行戒指」. <b>Every part carries an explicit colour, and so does the
     * whole component</b> - the format string's own literal text (the 「的」 / the spaces in English) has no
     * style of its own, so without the outer colour it would inherit the parent style that
     * {@code ItemStack#getHoverName()} adds, i.e. the vanilla rarity colour (yellow for UNCOMMON, ...).
     */
    public Component decorate(Component baseName) {
        return Component.translatable("ring_affix.simpleflightring.name",
                        prefix.displayName().withStyle(style -> style.withColor(rarity.color())),
                        rarity.displayName().withStyle(style -> style.withColor(rarity.color())),
                        baseName.copy().withStyle(style -> style.withColor(rarity.color())))
                .withStyle(style -> style.withColor(rarity.color()));
    }

    /** The tooltip line for this affix, coloured by rarity. */
    public MutableComponent tooltipLine() {
        return Component.translatable("ring_affix.simpleflightring.line",
                        prefix.displayName(), prefix.describe(rarity))
                .withStyle(style -> style.withColor(rarity.color()));
    }

    /**
     * One attribute change this affix grants, kept free of any Curios type so both versions can
     * turn it into their own modifier class (1.21.1 uses a plain {@code AttributeModifier} in a
     * {@code Multimap}, 26.1.2 uses {@code CurioAttributeModifiers}).
     */
    public record Change(Holder<Attribute> attribute, double amount,
                         AttributeModifier.Operation operation, String id) {
    }

    /**
     * The attribute changes of this affix. Only the plain attribute prefixes appear here; the
     * event-driven ones (thorned / reborn / precise / withering / venomous / piercing / brittle)
     * grant nothing through this channel and are handled by their own hooks - see the TODO below.
     */
    public java.util.List<Change> attributeChanges() {
        double value = prefix.value(rarity);
        String id = "affix_" + prefix.key();
        return switch (prefix) {
            case WARDING -> java.util.List.of(
                    new Change(Attributes.ARMOR, value, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, id));
            case VITAL -> java.util.List.of(
                    new Change(Attributes.MAX_HEALTH, value, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, id));
            case BRUTAL -> java.util.List.of(
                    new Change(Attributes.ATTACK_DAMAGE, value, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, id));
            case SWIFT -> java.util.List.of(
                    new Change(Attributes.MOVEMENT_SPEED, value, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, id));
            case AGILE -> java.util.List.of(
                    new Change(ApothicAttributesCompat.dodgeTarget(), value,
                            AttributeModifier.Operation.ADD_VALUE, id));
            case FORTUNATE -> java.util.List.of(
                    new Change(Attributes.LUCK, value, AttributeModifier.Operation.ADD_VALUE, id));
            default -> java.util.List.of();
        };
    }

    // ---- rolling (used by the loot function and by /simpleflightring reforge) ----

    /**
     * Rolls an affix: a uniformly random prefix unless one is given, and a weighted rarity unless one is
     * given. Anvil weights are used for the rarity, i.e. {@link Rarity#SACRED} is possible.
     */
    public static RingAffix roll(net.minecraft.util.RandomSource random, Prefix fixedPrefix, Rarity fixedRarity) {
        Prefix prefix = fixedPrefix != null
                ? fixedPrefix
                : Prefix.values()[random.nextInt(Prefix.values().length)];
        Rarity rarity = fixedRarity != null ? fixedRarity : rollRarity(random, true);
        return new RingAffix(prefix, rarity);
    }

    /**
     * Weighted rarity pick. {@code allowSacred} {@code false} gives the chest distribution, which is capped at
     * {@link Rarity#LEGENDARY} (SACRED's chest weight is 0, so it simply never comes up).
     */
    public static Rarity rollRarity(net.minecraft.util.RandomSource random, boolean allowSacred) {
        int total = 0;
        for (Rarity rarity : Rarity.values()) {
            total += allowSacred ? rarity.anvilWeight() : rarity.chestWeight();
        }
        int pick = random.nextInt(total);
        for (Rarity rarity : Rarity.values()) {
            pick -= allowSacred ? rarity.anvilWeight() : rarity.chestWeight();
            if (pick < 0) {
                return rarity;
            }
        }
        return Rarity.COMMON;
    }

    // TODO(framework): the event-driven halves of THORNED / REBORN / PRECISE / WITHERING /
    //  VENOMOUS / SUNDERING / BRITTLE (full-charge on-hit hook, totem save + charge bar,
    //  projectile damage share, armour / max-health shredding with the "dies when max health
    //  falls below current health" fallback) - see docs/PLAN-随机属性戒指.md §2 and §3.1.
}
