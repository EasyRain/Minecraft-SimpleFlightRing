package com.flightring;

import net.minecraft.world.item.ItemStack;

/**
 * The energy pool of the linked rings, currently used by the Magic Lining ability.
 * <p>
 * The pool lives on the ring itself (data component), like durability: a ring that
 * was never used is simply full. Being full removes the component again, so an
 * untouched ring stays pristine and no unnecessary syncing happens.
 * <p>
 * Rules of the design:
 * <ul>
 *   <li>Default pool: {@value #DEFAULT_MAX} energy, 1 damage costs 1 energy.</li>
 *   <li>Damage larger than the remaining energy is only partially absorbed; the
 *       remainder still hits the player.</li>
 *   <li>The pool starts refilling only after the wearer spent {@value #IDLE_SECONDS}
 *       seconds without taking damage, and always goes from empty to full in
 *       {@value #REFILL_SECONDS} seconds regardless of the pool size. Taking damage
 *       interrupts the refill.</li>
 * </ul>
 */public final class RingEnergy {

    /** Energy of a ring that has not been touched yet. */
    public static final float DEFAULT_MAX = 100.0F;

    /** Seconds without damage before the pool starts refilling. */
    public static final int IDLE_SECONDS = 10;

    /** Ticks without damage before the pool starts refilling. */
    public static final int IDLE_TICKS = IDLE_SECONDS * 20;

    /** A refill always takes this many seconds, no matter how large the pool is. */
    public static final int REFILL_SECONDS = 3;

    /** Ticks a complete refill takes. */
    public static final int REFILL_TICKS = REFILL_SECONDS * 20;

    /**
     * One refill step every 5 ticks (4 steps per second). Small enough for the bar to
     * look continuous, large enough to keep the item component (and its syncing) cheap.
     */
    public static final int REFILL_STEP_TICKS = 5;

    /** Energy added by one refill step, i.e. a full refill takes {@value #REFILL_SECONDS} seconds. */
    public static float refillStep(float max) {
        return max * REFILL_STEP_TICKS / (float) REFILL_TICKS;
    }

    private RingEnergy() {
    }

    /** Size of the ring's energy pool, or 0 when the ring has no pool at all. */
    public static float max(ItemStack stack) {
        return stack.getItem() instanceof FlightRingItem item ? item.getMaxEnergy() : 0.0F;
    }

    /** Current energy; an absent component means the pool is full. */
    public static float get(ItemStack stack) {
        float max = max(stack);
        Float stored = stack.get(ModDataComponents.RING_ENERGY.get());
        if (stored == null) {
            return max;
        }
        return clamp(stored, 0.0F, max);
    }

    /** Stores the pool; filling it up removes the component again (absent means full). */
    public static void set(ItemStack stack, float value) {
        float max = max(stack);
        if (max <= 0.0F) {
            return;
        }
        float clamped = clamp(value, 0.0F, max);
        if (clamped >= max) {
            stack.remove(ModDataComponents.RING_ENERGY.get());
        } else {
            stack.set(ModDataComponents.RING_ENERGY.get(), clamped);
        }
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
