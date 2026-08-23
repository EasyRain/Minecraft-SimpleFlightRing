package com.flightring;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Client-side HUD: draws the remaining flight time of the active ring(s).
 * <p>
 * The countdown is shown whenever the player carries at least one usable ring,
 * at the left-bottom corner by default. If the player carries several rings
 * (Curios flight ring slot, inventory and offhand), their remaining flight
 * times are summed up.
 * Position and visibility are controlled by {@link FlightRingConfig}
 * (config/flightring-client.toml) and can be edited in-game with Cloth Config.
 * <p>
 * Registered manually on the mod event bus by {@link FlightRingMod} (client side only).
 */
public class FlightHud {

    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "flight_timer");

    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, FlightHud::renderTimer);
    }

    private static void renderTimer(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!FlightRingConfig.SHOW_FLIGHT_TIMER.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        // Hide the countdown while the chat input is open (configurable), so it does
        // not overlap the semi-transparent chat box and hurt readability.
        if (FlightRingConfig.HIDE_WHILE_CHAT_OPEN.get() && minecraft.screen instanceof ChatScreen) {
            return;
        }
        // Prefer the server-pushed value: it is authoritative and includes rings
        // inside sophisticated backpacks whose contents are not reliably readable
        // client-side. Fall back to a local calculation when no update was received
        // recently (e.g. just joined the world).
        int totalRemainingSeconds;
        if (ClientFlightTime.isFresh()) {
            totalRemainingSeconds = ClientFlightTime.getSeconds();
        } else {
            totalRemainingSeconds = totalRemainingSeconds(minecraft.player);
        }
        if (totalRemainingSeconds <= 0) {
            return;
        }
        int minutes = totalRemainingSeconds / 60;
        int seconds = totalRemainingSeconds % 60;
        Component text = Component.translatable("hud.flightring.flight_time", minutes, seconds);

        int x = FlightRingConfig.HUD_X.get();
        int y = minecraft.getWindow().getGuiScaledHeight() - FlightRingConfig.HUD_Y.get() - 9;
        guiGraphics.drawString(minecraft.font, text, x, y, 0xFFFFFF, true);
    }

    /**
     * Sums the remaining flight time (in seconds) of every usable ring the player
     * carries: Curios flight ring slot (if loaded), main inventory, offhand and
     * sophisticated backpacks (if loaded). A fully consumed ring contributes nothing.
     */
    private static int totalRemainingSeconds(Player player) {
        int total = 0;
        if (CuriosCompat.isLoaded()) {
            total += remainingSeconds(CuriosCompat.findRingInSlot(player));
        }
        for (ItemStack stack : player.getInventory().items) {
            total += remainingSeconds(stack);
        }
        total += remainingSeconds(player.getInventory().offhand.get(0));
        if (BackpackCompat.isLoaded()) {
            for (BackpackCompat.BackpackRing ring : BackpackCompat.findRingsInBackpacks(player)) {
                total += remainingSeconds(ring.stack());
            }
        }
        return total;
    }

    private static int remainingSeconds(ItemStack stack) {
        if (isUsableRing(stack)) {
            return stack.getMaxDamage() - stack.getDamageValue();
        }
        return 0;
    }

    private static boolean isUsableRing(ItemStack stack) {
        return stack.getItem() instanceof FlightRingItem && stack.getDamageValue() < stack.getMaxDamage();
    }

    private FlightHud() {
    }
}
