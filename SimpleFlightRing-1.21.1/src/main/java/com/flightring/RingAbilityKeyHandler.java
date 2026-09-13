package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Server side of the ring ability key (V by default, see {@link ModKeyMappings}).
 * <p>
 * The client only says "the key was pressed"; everything that matters is decided here
 * from the ring the player is really wearing, so the key cannot be spoofed into
 * triggering a ring the player does not have.
 * <p>
 * Every ability implemented so far (Magic Lining, Kinetic Deflection, Burst Totem)
 * triggers on its own, so pressing the key currently just reports the ring it would
 * fire. ACTIVE abilities hook in at the marked spot below.
 */
public final class RingAbilityKeyHandler {

    public static void onAbilityKey(ServerPlayer player, int slot) {
        ItemStack ring = wornAbilityRing(player);
        if (ring.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.simpleflightring.ability_no_ring").withStyle(ChatFormatting.GRAY), true);
            return;
        }

        if (ring.getItem() instanceof FlightRingItem item && item.hasAbility(RingAbility.SCULK_SOUL)) {
            SculkSoulAbility.tryFireSonicBoom(player);
            return;
        }

        // TODO Other active abilities go here as they are added: pick the ability for `slot`,
        //      verify the ring is charged (RingEnergy) and let it fire. Until then:
        player.displayClientMessage(Component.translatable("message.simpleflightring.ability_passive",
                ring.getHoverName()).withStyle(ChatFormatting.GRAY), true);
        FlightRingMod.LOGGER.debug("[FlightRing] {} pressed the ability key with {} (slot {})",
                player.getName().getString(), ring.getHoverName().getString(), slot);
    }

    /**
     * The ring worn in the Curios flight ring slot when it has special abilities, or an
     * empty stack. Abilities never work from the inventory (same rule as the linked rings'
     * attributes), so the ability key does not look there either.
     */
    private static ItemStack wornAbilityRing(ServerPlayer player) {
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        return hasAbilities(ring) ? ring : ItemStack.EMPTY;
    }

    private static boolean hasAbilities(ItemStack stack) {
        return stack.getItem() instanceof FlightRingItem ring && !ring.getAbilities().isEmpty();
    }

    private RingAbilityKeyHandler() {
    }
}
