package com.flightring;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The state behind the desert ring's danger sense, kept deliberately free of any client-only
 * class so the mixin that reads it ({@code EntityGlowMixin}) can stay a common mixin: the sets
 * are only ever filled on the client - {@link DesertSenseHandler} fills the sensed one every ten
 * ticks, the {@code desert_hostiles} payload ({@link DesertHostileSync}) fills the grudges - and
 * the mixin only asks about entities while {@code level().isClientSide()} is true.
 * <p>
 * A sensed creature is outlined for the wearer alone: <b>red</b> while it is hostile to the
 * wearer, <b>white</b> for all the rest. Hostility is decided on <b>behaviour</b>, not on the
 * creature's class - a creature is red while it hunts the wearer or holds a grudge against them.
 * That is what makes neutral mobs come out right: a zombified piglin, an enderman or a spider is
 * white until it is provoked, and so is a monster from another mod that is neutral by its own
 * rules, while a zombie that has locked onto the wearer is red the moment it does. Only living
 * creatures are ever put in here - dropped items, experience orbs, boats, minecarts and the like
 * are not creatures, and decorative armour stands are left out as well even though they
 * technically are {@link LivingEntity}.
 */
public final class DesertSense {

    /** Outline colours, as vanilla expects them (it adds the alpha itself). */
    public static final int FRIENDLY_COLOR = 0xFFFFFF;
    public static final int HOSTILE_COLOR = 0xFF0000;

    /** Entity ids the local client should outline. */
    private static final Set<Integer> SENSED = new HashSet<>();
    /**
     * Entity ids the server says are hunting the local player. Kept apart from {@link #SENSED}
     * because the two arrive at different times: a grudge can start after the creature was
     * outlined, and the colour is read per frame, so recolouring must not wait for a rescan.
     */
    private static final Set<Integer> HOSTILE = new HashSet<>();

    /** True if this very entity should appear glowing for the ring wearer. */
    public static boolean senses(Entity entity) {
        return !SENSED.isEmpty() && SENSED.contains(entity.getId());
    }

    /** The outline colour of a sensed entity. */
    public static int colorOf(Entity entity) {
        return colorFor(entity);
    }

    /** True for the entities the danger sense may outline: creatures, not things. */
    public static boolean isSenseable(Entity entity) {
        return entity instanceof LivingEntity
                && !(entity instanceof ArmorStand)
                && !entity.isSpectator();
    }

    /** The colour of a sensed creature: red while it is hostile to the wearer, white otherwise. */
    public static int colorFor(Entity entity) {
        return HOSTILE.contains(entity.getId()) ? HOSTILE_COLOR : FRIENDLY_COLOR;
    }

    /** Replaces the sensed set (client only, called from the client tick). */
    public static void replace(Collection<Integer> sensed) {
        SENSED.clear();
        SENSED.addAll(sensed);
    }

    /** Replaces the grudges the server reported for this client (client only). */
    public static void setHostile(Collection<Integer> hostile) {
        HOSTILE.clear();
        HOSTILE.addAll(hostile);
    }

    /** Forgets everything - the ring is off, or the player left the level. */
    public static void clear() {
        SENSED.clear();
        HOSTILE.clear();
    }

    private DesertSense() {
    }
}
