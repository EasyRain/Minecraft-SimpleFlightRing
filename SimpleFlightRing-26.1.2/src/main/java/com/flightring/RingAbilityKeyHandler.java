package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Server side of the ring ability key (V by default, see {@link ModKeyMappings}).
 * <p>
 * The client only says "the key was pressed"; everything that matters is decided here
 * from the ring the player is really wearing, so the key cannot be spoofed into
 * triggering a ring the player does not have.
 * <p>
 * The ring must actually work: a drained ring (durability used up) fires nothing, just
 * like it stops granting flight. Rings whose abilities are all passive only report that.
 */
public final class RingAbilityKeyHandler {

    public static void onAbilityKey(ServerPlayer player, int slot) {
        ItemStack ring = wornAbilityRing(player);
        if (ring.isEmpty()) {
            player.sendOverlayMessage(
                    Component.translatable("message.simpleflightring.ability_no_ring").withStyle(ChatFormatting.GRAY));
            return;
        }

        if (ring.getItem() instanceof FlightRingItem item && item.hasAbility(RingAbility.SCULK_SOUL)) {
            SculkSoulAbility.tryFireSonicBoom(player, ring);
            return;
        }
        if (ring.getItem() instanceof FlightRingItem item && item.hasAbility(RingAbility.MINER_VETERAN)) {
            MinerVeteranAbility.tryDetonate(player, ring);
            return;
        }
        if (ring.getItem() instanceof FlightRingItem item && item.hasAbility(RingAbility.RAID_PLUNDER)) {
            RaidFangAbility.tryCast(player, ring);
            return;
        }

        // TODO Other active abilities go here as they are added: pick the ability for `slot`,
        //      verify the ring is charged (RingEnergy) and let it fire. Until then:
        player.sendOverlayMessage(Component.translatable("message.simpleflightring.ability_passive",
                ring.getHoverName()).withStyle(ChatFormatting.GRAY));
        FlightRingMod.LOGGER.debug("[FlightRing] {} pressed the ability key with {} (slot {})",
                player.getName().getString(), ring.getHoverName().getString(), slot);
    }

    /**
     * The ring worn in the Curios flight ring slot when it has special abilities and still
     * has durability left, or an empty stack. Abilities never work from the inventory (the
     * same rule as the linked rings' attributes), so the ability key does not look there.
     */
    private static ItemStack wornAbilityRing(ServerPlayer player) {
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        return hasAbilities(ring) ? ring : ItemStack.EMPTY;
    }

    private static boolean hasAbilities(ItemStack stack) {
        return stack.getItem() instanceof FlightRingItem ring
                && !ring.getAbilities().isEmpty()
                && ring.isUsable(stack);
    }

    private RingAbilityKeyHandler() {
    }
}
