package com.flightring;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The one and only hint about the hidden broken dimension, exactly as specified: while carrying the
 * ender ring, stepping through a nether portal prints a single line in chat.
 * <p>
 * "Carrying" means anywhere on the player - equipped or not, in the inventory, the offhand, a
 * sophisticated backpack, or the Curios slot. The line is shown <b>once per player</b> and never to
 * players who already rewrote a portal (or have been to the dimension) themselves.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class BrokenRingHint {

    /** See the lang files: 「末影戒指似乎对传送门产生了共鸣」. */
    private static final String MESSAGE_KEY = "message.simpleflightring.ender_ring_resonance";

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
        if (!carriesEnderRing(player)) {
            return;
        }
        BrokenPortalData.setHintShown(player);
        player.sendSystemMessage(Component.translatable(MESSAGE_KEY));
    }

    private static boolean carriesEnderRing(ServerPlayer player) {
        if (CuriosCompat.isLoaded() && isEnderRing(CuriosCompat.findRingInSlot(player))) {
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (isEnderRing(stack)) {
                return true;
            }
        }
        if (isEnderRing(player.getInventory().offhand.get(0))) {
            return true;
        }
        if (BackpackCompat.isLoaded()) {
            for (BackpackCompat.BackpackRing ring : BackpackCompat.findRingsInBackpacks(player)) {
                if (isEnderRing(ring.stack())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isEnderRing(ItemStack stack) {
        return stack.getItem() instanceof FlightRingItem ring && ring.hasAbility(RingAbility.WARP_NEXUS);
    }

    private BrokenRingHint() {
    }
}
