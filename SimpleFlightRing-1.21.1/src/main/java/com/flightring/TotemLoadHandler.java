package com.flightring;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Shift-right-click with a Totem of Undying feeds it straight into the worn raid ring, so the player does not
 * have to take the ring off and craft. Exactly the same trade as the {@code raid_charge} recipe: one totem is
 * worth {@link RaidCharges#PER_TOTEM} charges and only fits while {@link RaidCharges#canTakeTotem(ItemStack)}
 * says so. The crafting recipe stays untouched.
 */
public final class TotemLoadHandler {

    private TotemLoadHandler() {
    }

    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isShiftKeyDown()) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (!held.is(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        if (!(ring.getItem() instanceof FlightRingItem item) || !item.hasAbility(RingAbility.RAID_PLUNDER)) {
            return;
        }
        if (!RaidCharges.canTakeTotem(ring)) {
            player.sendSystemMessage(
                    Component.translatable("message.simpleflightring.totem_full", RaidCharges.of(ring)),
                    true);
            return;
        }
        RaidCharges.set(ring, RaidCharges.of(ring) + RaidCharges.PER_TOTEM);
        held.shrink(1);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 0.8F, 1.2F);
        player.sendSystemMessage(
                Component.translatable("message.simpleflightring.totem_loaded", RaidCharges.of(ring)),
                true);
        event.setCanceled(true);
    }
}
