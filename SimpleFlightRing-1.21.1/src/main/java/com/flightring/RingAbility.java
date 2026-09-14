package com.flightring;

/**
 * Special abilities of the relic rings and of the linked (AlltheModium chain) rings.
 * <p>
 * Upgrades inherit the previous tier's abilities: Vibranium also has whatever
 * Allthemodium grants, and Unobtainium has all of them (see {@code ModItems}).
 * The abilities themselves only work while the ring is worn in the Curios
 * "flight ring" slot.
 * <p>
 * Each ability keeps the colour of the ring it belongs to, so the tooltip shows the
 * whole inherited chain in its own tints (Allthemodium golden, Vibranium teal,
 * Unobtainium violet, Sculk cyan) - the same palettes the ring textures are drawn from.
 */
public enum RingAbility {

    /** Allthemodium: spends the ring's energy pool to absorb incoming damage. */
    MAGIC_LINING("magic_lining", 0xFFFFC24A, 3),

    /** Vibranium: bounces ranged attacks away while more than half of the pool is left. */
    KINETIC_DEFLECTION("kinetic_deflection", 0xFF4BE3A8, 3),

    /** Unobtainium: survives a fatal hit with a block-safe explosion around the wearer. */
    BURST_TOTEM("burst_totem", 0xFFC06BF5, 4),

    /**
     * Sculk relic ring: darkness immunity, warden neutrality, silence in the deep dark and
     * the V key sonic boom. Its last tooltip line names the ability key, so it is filled in
     * with the player's actual binding by {@link FlightRingItem#abilityHints()}.
     */
    SCULK_SOUL("sculk_soul", 0xFF29DFEB, 5, 5),

    /**
     * Miner relic ring: permanent Night Vision, depth scaled Haste, TNT immunity and a
     * block-breaking TNT blast on the ability key.
     */
    MINER_VETERAN("miner_veteran", 0xFFC0C0C0, 5, 5),

    /**
     * Emerald relic ring: the village hero's blessing is permanent and its strength is the
     * level of the raid the ring was carried through (see {@link HeroLevel}), illagers take
     * double damage from the wearer, drop twice the loot and every hostile mob has a small
     * chance to leave an emerald behind. Passive only - the emerald ring has no ability key,
     * and the emerald chance keeps no tooltip line of its own.
     */
    EMERALD_HERO("emerald_hero", 0xFF54E18E, 4, 0, 1),

    /**
     * Ocean relic ring: the sea's own blessing. Water breathing and dolphin's grace forever,
     * night vision and clear water while submerged, aquatic monsters treat the wearer as one of
     * their own (but still hit back), half damage while standing in an ocean biome, a whirlpool
     * that slows every nearby enemy and flames that go out on their own. Passive only - the
     * ocean ring has no ability key.
     */
    OCEAN_FAVORED("ocean_favored", 0xFF2BB8D8, 5);

    private final String key;
    private final int color;
    private final int descriptionLines;
    private final int keyLine;
    private final int levelLine;

    RingAbility(String key, int color, int descriptionLines) {
        this(key, color, descriptionLines, 0, 0);
    }

    RingAbility(String key, int color, int descriptionLines, int keyLine) {
        this(key, color, descriptionLines, keyLine, 0);
    }

    RingAbility(String key, int color, int descriptionLines, int keyLine, int levelLine) {
        this.key = key;
        this.color = color;
        this.descriptionLines = descriptionLines;
        this.keyLine = keyLine;
        this.levelLine = levelLine;
    }

    /** Suffix of this ability's translation keys ({@code tooltip.simpleflightring.<key>_title/_desc}). */
    public String key() {
        return key;
    }

    /**
     * Number of description lines this ability shows: the first uses the {@code _desc}
     * key and the following ones {@code _desc2}, {@code _desc3}, ... Every line is its
     * own component, because a {@code \n} inside a component is NOT turned into a line
     * break by the vanilla tooltip (1.21.1 renders it as a missing glyph box).
     */
    public int descriptionLines() {
        return descriptionLines;
    }

    /**
     * 1-based number of the description line that names the ability key (0 = none), so
     * {@link FlightRingItem#abilityHints()} can fill in the key the player actually bound.
     */
    public int keyLine() {
        return keyLine;
    }

    /**
     * 1-based number of the description line that shows the ring's strength (0 = none), so
     * {@link FlightRingItem#abilityHints()} can fill in the level the ring was charged at
     * (see {@link HeroLevel}). Only the emerald ring uses it so far.
     */
    public int levelLine() {
        return levelLine;
    }

    /**
     * RGB colour of the ring that owns this ability, taken from that ring's metal palette
     * (Allthemodium {@code 0xFFFF8B04}→{@code 0xFFFFFFBA}, Vibranium {@code 0x1BB38A}→
     * {@code 0x73FFB9}, Unobtainium {@code 0xA82CE3}→{@code 0xEA84F5}), kept in the
     * readable middle so the title stands out on the dark tooltip background.
     */
    public int color() {
        return color;
    }
}
