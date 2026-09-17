package com.flightring;

import net.minecraft.world.item.ItemStack;

/**
 * The totem charges of a raid ring: the ring works like a totem of undying while it holds any,
 * spending one charge per saved life, and every totem of undying crafted into it adds
 * {@link #PER_TOTEM} of them - so a totem is worth twice as much inside the ring as it is on its
 * own. The pool is capped at {@link #MAX}; a ring at nine charges can no longer take a totem,
 * because the totem always adds two.
 */
public final class RaidCharges {

    /** Highest charge a ring can hold. */
    public static final int MAX = 10;
    /** Charges one totem of undying is worth when crafted into a ring. */
    public static final int PER_TOTEM = 2;

    /** Charges held by this ring; a ring without the component is empty. */
    public static int of(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.RAID_CHARGES.get(), 0);
    }

    /** Writes the charge count, removing the component when the pool is empty. */
    public static void set(ItemStack stack, int charges) {
        if (charges <= 0) {
            stack.remove(ModDataComponents.RAID_CHARGES.get());
        } else {
            stack.set(ModDataComponents.RAID_CHARGES.get(), charges);
        }
    }

    /** True while another totem still fits inside this ring. */
    public static boolean canTakeTotem(ItemStack stack) {
        return of(stack) + PER_TOTEM <= MAX;
    }

    private RaidCharges() {
    }
}
