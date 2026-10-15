package com.flightring;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The one and only hint about the hidden broken dimension, exactly as specified: while carrying a
 * flight ring, stepping through a nether portal prints a single line in chat.
 * <p>
 * "Carrying" means anywhere on the player - equipped or not, in the inventory, the offhand, a
 * sophisticated backpack, or the Curios slot. The line is shown <b>once per player</b> and never to
 * players who already built a portal (or have been to the dimension) themselves.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class BrokenRingHint {

    /** See the lang files: 「飞行戒指似乎对传送门产生了共鸣」. */
    private static final String MESSAGE_KEY = "message.simpleflightring.flight_ring_resonance";

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // Either direction counts as "going through a nether portal": a player may well make the
        // trip back from the nether first, and that must hint them just the same.
        boolean netherTrip = event.getTo().equals(Level.NETHER) || event.getFrom().equals(Level.NETHER);
        if (!netherTrip) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (BrokenPortalData.hintShown(player) || BrokenPortalData.knowsDimension(player)) {
            return;
        }
        if (!carriesFlightRing(player)) {
            return;
        }
        BrokenPortalData.setHintShown(player);
        player.sendSystemMessage(Component.translatable(MESSAGE_KEY));
    }

    private static boolean carriesFlightRing(ServerPlayer player) {
        if (CuriosCompat.isLoaded() && isFlightRing(CuriosCompat.findRingInSlot(player))) {
            return true;
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (isFlightRing(stack)) {
                return true;
            }
        }
        if (isFlightRing(player.getItemBySlot(EquipmentSlot.OFFHAND))) {
            return true;
        }
        if (BackpackCompat.isLoaded()) {
            for (BackpackCompat.BackpackRing ring : BackpackCompat.findRingsInBackpacks(player)) {
                if (isFlightRing(ring.stack())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Any flight ring counts, the same rule the portal itself uses. */
    private static boolean isFlightRing(ItemStack stack) {
        return stack.getItem() instanceof FlightRingItem;
    }

    private BrokenRingHint() {
    }
}
