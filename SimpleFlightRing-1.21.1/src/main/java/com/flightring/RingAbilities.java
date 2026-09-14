package com.flightring;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Shared helpers for the special abilities of the linked rings: they all work only while
 * the ring is worn in the Curios "flight ring" slot, exactly like the ring's attributes.
 */
final class RingAbilities {

    private RingAbilities() {
    }

    /**
     * The ring the player wears in the Curios flight ring slot, when that ring has the
     * given ability; {@link ItemStack#EMPTY} otherwise (also when Curios is absent).
     */
    static ItemStack wornRing(ServerPlayer player, RingAbility ability) {
        if (!CuriosCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        if (ring.getItem() instanceof FlightRingItem item && item.hasAbility(ability)) {
            return ring;
        }
        return ItemStack.EMPTY;
    }

    /**
     * The worn ring when it has an energy pool, no matter which ability spends it: the
     * pool is refilled and synced for every one of them.
     */
    static ItemStack wornEnergyRing(ServerPlayer player) {
        if (!CuriosCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        return RingEnergy.max(ring) > 0.0F ? ring : ItemStack.EMPTY;
    }

    /**
     * Damage multiplier the ring's 力量 (Power) enchantment grants to its special ability:
     * +25% per level, so level V hits for 2.25 times the base damage. Uses the VANILLA
     * {@code minecraft:power} enchantment (the rings that have an active ability are added
     * to {@code #minecraft:enchantable/bow}, which is what makes the table offer it);
     * a ring without it returns 1.0.
     */
    static float abilityDamageMultiplier(ItemStack ring) {
        return 1.0F + 0.25F * RingEnergy.enchantLevel(ring, Enchantments.POWER);
    }
}
