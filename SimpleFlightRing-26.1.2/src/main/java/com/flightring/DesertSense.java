package com.flightring;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;

import java.util.HashMap;
import java.util.Map;

/**
 * The state behind the desert ring's danger sense, kept deliberately free of any client-only
 * class so the mixin that reads it ({@code EntityGlowMixin}) can stay a common mixin: the map is
 * only ever filled on the client by {@link DesertSenseHandler}, and the mixin only asks about
 * entities while {@code level().isClientSide()} is true.
 * <p>
 * Entity ids are mapped to the outline colour the wearer should see: <b>red</b> for hostile
 * creatures ({@link Enemy}) and <b>white</b> for friendly or neutral ones. Only living creatures
 * are ever put in here - dropped items, experience orbs, boats, minecarts and the like are not
 * creatures, and decorative armour stands are left out as well even though they technically are
 * {@link LivingEntity}.
 */
public final class DesertSense {

    /** Outline colours, as vanilla expects them (it adds the alpha itself). */
    public static final int FRIENDLY_COLOR = 0xFFFFFF;
    public static final int HOSTILE_COLOR = 0xFF0000;

    /** Entity id -> outline colour, for the local client only. */
    private static final Map<Integer, Integer> SENSED = new HashMap<>();

    /** True if this very entity should appear glowing for the ring wearer. */
    public static boolean senses(Entity entity) {
        return !SENSED.isEmpty() && SENSED.containsKey(entity.getId());
    }

    /** The outline colour of a sensed entity. */
    public static int colorOf(Entity entity) {
        return SENSED.getOrDefault(entity.getId(), FRIENDLY_COLOR);
    }

    /** True for the entities the danger sense may outline: creatures, not things. */
    public static boolean isSenseable(Entity entity) {
        return entity instanceof LivingEntity
                && !(entity instanceof ArmorStand)
                && !entity.isSpectator();
    }

    /** The colour of a sensed creature: red for the hostile ones, white for the rest. */
    public static int colorFor(Entity entity) {
        return entity instanceof Enemy ? HOSTILE_COLOR : FRIENDLY_COLOR;
    }

    /** Replaces the sensed set (client only, called from the client tick). */
    public static void replace(Map<Integer, Integer> sensed) {
        SENSED.clear();
        SENSED.putAll(sensed);
    }

    /** Forgets everything - the ring is off, or the player left the level. */
    public static void clear() {
        SENSED.clear();
    }

    private DesertSense() {
    }
}
