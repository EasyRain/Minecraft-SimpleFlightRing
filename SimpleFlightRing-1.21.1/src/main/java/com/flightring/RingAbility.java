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
    OCEAN_FAVORED("ocean_favored", 0xFF2BB8D8, 5),

    /**
     * Desert relic ring: the desert wastes nothing - a permanent point of Luck (a real attribute
     * bonus, so a Luck potion stacks on top of it), immunity to Hunger and Poison, half again as
     * much food value out of everything eaten, and a sixth sense that outlines every living
     * creature within 32 blocks for the wearer alone, white until it turns on the wearer and red
     * while it is hunting them. Passive only - the desert ring has no ability key.
     */
    DESERT_GUIDE("desert_guide", 0xFFF0CE8C, 5),

    /**
     * Raid relic ring: the illager's own blessing, "Great Plunder!". The wearer counts as one of
     * the illagers - raiders leave them alone (but hit back), villagers refuse to trade and flee,
     * iron golems turn on them - and lives off the village instead: emeralds buy ominous bottles
     * from raid captains and totems from evokers, slaying a villager yields emeralds and that
     * villager's trade goods, Bad Omen cannot touch the wearer, and the ring itself holds totem
     * charges (see {@link RaidCharges}) which save their life the way a totem of undying would.
     * Its last line names the ability key, which summons the evoker's fangs.
     */
    RAID_PLUNDER("raid_plunder", 0xFFCC4E46, 5, 5, 0, 4),

    /**
     * Infernal relic ring: "Flame Lord". The wearer never burns, cannot be withered and sees
     * straight through lava, and the ability key sets everything around them alight with eternal
     * soul fire - a custom effect that burns forever, that only an ocean ring can shrug off, and
     * that fire resistance potions wear down one level at a time. Smeltable drops caught in the
     * blast come out of it already smelted. The last line names the ability key.
     */
    FLAME_LORD("flame_lord", 0xFFFF8B34, 4, 4),

    /**
     * Ender relic ring: "Warp Nexus". The wearer walks among the End's own - endermen, endermites,
     * shulkers and phantoms never take them as a target, an enderman's stare no longer angers one,
     * phantoms stop coming for them altogether, levitation cannot touch them, and every projectile
     * aimed at them is thrown away at random the way an enderman dodges an arrow. The ability key
     * blinks them fifteen blocks forward, through whatever is in between, and the 能量迸发
     * enchantment stretches that jump instead of doing anything to damage. Its last line names the
     * ability key.
     */
    WARP_NEXUS("warp_nexus", 0xFFB074F4, 5, 5);

    private final String key;
    private final int color;
    private final int descriptionLines;
    private final int keyLine;
    private final int levelLine;
    private final int chargeLine;

    RingAbility(String key, int color, int descriptionLines) {
        this(key, color, descriptionLines, 0, 0, 0);
    }

    RingAbility(String key, int color, int descriptionLines, int keyLine) {
        this(key, color, descriptionLines, keyLine, 0, 0);
    }

    RingAbility(String key, int color, int descriptionLines, int keyLine, int levelLine) {
        this(key, color, descriptionLines, keyLine, levelLine, 0);
    }

    RingAbility(String key, int color, int descriptionLines, int keyLine, int levelLine, int chargeLine) {
        this.key = key;
        this.color = color;
        this.descriptionLines = descriptionLines;
        this.keyLine = keyLine;
        this.levelLine = levelLine;
        this.chargeLine = chargeLine;
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
     * 1-based number of the description line that shows the ring's totem charges (0 = none), so
     * {@link FlightRingItem#abilityHints(ItemStack)} can fill in how many lives the ring can still
     * save (see {@link RaidCharges}). Only the raid ring uses it so far.
     */
    public int chargeLine() {
        return chargeLine;
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
