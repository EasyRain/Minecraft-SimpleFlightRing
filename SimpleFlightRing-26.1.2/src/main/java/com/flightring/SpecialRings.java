package com.flightring;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * The two special rings (Stable / Powered) carry INTRINSIC (built-in) enchantments.
 * <p>
 * They live in this mod's own {@code intrinsic_enchantments} component instead of the
 * vanilla enchantments component, so the grindstone - or anything else that strips
 * enchantments - cannot remove them. Every source of the item (crafting table,
 * creative tab, commands) gets them.
 * <p>
 * The effective level is the higher of the intrinsic level and the real enchantment
 * level: a ring with Rocket Boost I built in can still be enchanted to a higher
 * Rocket Boost, and that higher level is used. See {@code EnchantmentHelperMixin}.
 */
public final class SpecialRings {

    /** Level of each built-in enchantment the ring is created with. */
    public static final int BASE_LEVEL = 1;

    private SpecialRings() {
    }

    /** True for the two special rings. */
    public static boolean isSpecial(ItemStack stack) {
        return stack.is(ModItems.STABLE_FLIGHT_RING.get()) || stack.is(ModItems.POWERED_FLIGHT_RING.get());
    }

    /** True if the enchantment belongs to the ring's built-in set. */
    public static boolean isBuiltIn(ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.is(ModItems.STABLE_FLIGHT_RING.get())) {
            return ModEnchantments.FLIGHT_STABILITY.equals(key);
        }
        if (stack.is(ModItems.POWERED_FLIGHT_RING.get())) {
            return ModEnchantments.ROCKET_BOOST.equals(key) || Enchantments.EFFICIENCY.equals(key);
        }
        return false;
    }

    /** Applies the ring's built-in enchantments; no-op for the tiered rings. */
    public static void applyIntrinsic(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.is(ModItems.STABLE_FLIGHT_RING.get())) {
            setIntrinsic(stack, registries.holderOrThrow(ModEnchantments.FLIGHT_STABILITY), BASE_LEVEL);
        } else if (stack.is(ModItems.POWERED_FLIGHT_RING.get())) {
            setIntrinsic(stack, registries.holderOrThrow(ModEnchantments.ROCKET_BOOST), BASE_LEVEL);
            setIntrinsic(stack, registries.holderOrThrow(Enchantments.EFFICIENCY), BASE_LEVEL);
        }
    }

    /** Replaces the level of one intrinsic (built-in) enchantment. */
    public static void setIntrinsic(ItemStack stack, Holder<Enchantment> enchantment, int level) {
        ItemEnchantments current = stack.getOrDefault(
                ModDataComponents.INTRINSIC_ENCHANTMENTS.get(), ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        mutable.set(enchantment, level);
        stack.set(ModDataComponents.INTRINSIC_ENCHANTMENTS.get(), mutable.toImmutable());
    }
}
