package com.flightring;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Client-side HUD: draws the remaining flight time of the active ring(s).
 * <p>
 * The countdown is shown whenever the player carries at least one usable ring,
 * at the left-bottom corner by default. If the player carries several rings
 * (Curios flight ring slot, inventory and offhand), their remaining flight
 * times are summed up.
 * Position and visibility are controlled by {@link FlightRingConfig}
 * (config/simpleflightring-client.toml) and can be edited in-game with Cloth Config.
 * <p>
 * Registered manually on the mod event bus by {@link FlightRingMod} (client side only).
 */
public class FlightHud {

    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "flight_timer");

    /** Show the hours unit once the remaining flight time reaches 1000 minutes (60 000 seconds). */
    private static final int SHOW_HOURS_THRESHOLD = 60_000;

    /**
     * Size of the energy bar of rings with abilities (Magic Lining). The label is drawn
     * inside the bar, so the bar itself is the only thing that can overlap other HUD
     * elements and no text runs off towards the hotbar. Kept at 80 px wide: any wider
     * and the bar itself reaches the hotbar.
     */
    private static final int ENERGY_BAR_WIDTH = 80;
    private static final int ENERGY_BAR_HEIGHT = 10;

    /**
     * Energy actually drawn: the server sends refill steps, this value chases them at
     * the pool's refill rate so the bar grows smoothly instead of jumping in steps.
     */
    private static float displayedEnergy = -1.0F;
    private static long lastEnergyFrameMillis;

    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, FlightHud::renderTimer);
    }

    private static void renderTimer(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
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

        int x = FlightRingConfig.HUD_X.get();
        int textY = guiGraphics.guiHeight() - FlightRingConfig.HUD_Y.get() - 9;

        Component timerText = flightTimeText(minecraft.player);
        // The energy bar sits right above the countdown. Indestructible rings hide the
        // countdown, so the bar then takes its place.
        renderEnergyBar(guiGraphics, minecraft, x, timerText == null ? textY : textY - ENERGY_BAR_HEIGHT - 3);
        if (timerText != null) {
            // 26.1.2 uses ARGB colors: 0xFFFFFFFF is opaque white.
            guiGraphics.text(minecraft.font, timerText, x, textY, 0xFFFFFFFF, true);
        }
    }

    /** The countdown line, or {@code null} when there is no flight time to show. */
    private static Component flightTimeText(Player player) {
        // Prefer the server-pushed value: it is authoritative and includes rings
        // inside sophisticated backpacks whose contents are not reliably readable
        // client-side. Fall back to a local calculation when no update was received
        // recently (e.g. just joined the world).
        int totalRemainingSeconds = ClientFlightTime.isFresh()
                ? ClientFlightTime.getSeconds()
                : totalRemainingSeconds(player);
        if (totalRemainingSeconds <= 0) {
            return null;
        }
        if (totalRemainingSeconds >= SHOW_HOURS_THRESHOLD) {
            // Very long flight times (>= 1000 minutes): show hours too.
            int hours = totalRemainingSeconds / 3600;
            int minutes = (totalRemainingSeconds % 3600) / 60;
            int seconds = totalRemainingSeconds % 60;
            return Component.translatable("hud.simpleflightring.flight_time_long", hours, minutes, seconds);
        }
        int minutes = totalRemainingSeconds / 60;
        int seconds = totalRemainingSeconds % 60;
        return Component.translatable("hud.simpleflightring.flight_time", minutes, seconds);
    }

    /**
     * Energy bar of the ability ring worn in the Curios slot (Magic Lining). The values
     * are pushed by the server, so the bar shows the pool even though the client only
     * sees the item's synced copy between updates.
     */
    private static void renderEnergyBar(GuiGraphicsExtractor guiGraphics, Minecraft minecraft, int x, int y) {
        if (!ClientRingEnergy.isActive()) {
            displayedEnergy = -1.0F;
            lastEnergyFrameMillis = 0L;
            return;
        }
        float max = ClientRingEnergy.maxEnergy();
        float target = Math.max(0.0F, Math.min(max, ClientRingEnergy.energy()));

        // Smooth the refill: the server sends steps, so the drawn value advances at the
        // pool's own refill rate (a full refill in REFILL_SECONDS). Damage is instant.
        long now = Util.getMillis();
        float elapsed = lastEnergyFrameMillis == 0L ? 0.0F : Math.min(0.25F, (now - lastEnergyFrameMillis) / 1000.0F);
        lastEnergyFrameMillis = now;
        if (displayedEnergy < 0.0F || target < displayedEnergy) {
            displayedEnergy = target;
        } else if (target > displayedEnergy) {
            displayedEnergy = Math.min(target, displayedEnergy + (max / RingEnergy.REFILL_SECONDS) * elapsed);
        }

        int filled = Math.round(ENERGY_BAR_WIDTH * (displayedEnergy / max));
        // Magic purple: dark frame, dim track, bright fill.
        guiGraphics.fill(x - 1, y - 1, x + ENERGY_BAR_WIDTH + 1, y + ENERGY_BAR_HEIGHT + 1, 0xFF1B1230);
        guiGraphics.fill(x, y, x + ENERGY_BAR_WIDTH, y + ENERGY_BAR_HEIGHT, 0xFF3A2A66);
        if (filled > 0) {
            guiGraphics.fill(x, y, x + filled, y + ENERGY_BAR_HEIGHT, 0xFF9A6BFF);
        }

        // The label lives inside the bar, so nothing sticks out towards the hotbar.
        Component label = Component.translatable("hud.simpleflightring.energy",
                Math.round(displayedEnergy), Math.round(max));
        // Dim red while the pool is empty (no more damage absorption).
        int color = displayedEnergy > 0.0F ? 0xFFFFFFFF : 0xFFFF8080;
        int labelX = x + (ENERGY_BAR_WIDTH - minecraft.font.width(label)) / 2;
        guiGraphics.text(minecraft.font, label, labelX, y + 1, color, true);
    }

    /**
     * Sums the remaining flight time (in seconds) of every usable ring the player
     * carries: Curios flight ring slot (if loaded), main inventory, offhand and
     * sophisticated backpacks (if loaded). A fully consumed ring contributes nothing.
     * <p>
     * Each ring contributes {@code remaining durability points * (1 + Unbreaking level)}
     * seconds, i.e. the actual flight time taking the ring's enchantments into account.
     */
    private static int totalRemainingSeconds(Player player) {
        // An indestructible ring means infinite flight time: hide the countdown (-1).
        if (hasIndestructibleRing(player)) {
            return -1;
        }
        int total = 0;
        if (CuriosCompat.isLoaded()) {
            total += remainingSeconds(player, CuriosCompat.findRingInSlot(player));
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            total += remainingSeconds(player, stack);
        }
        total += remainingSeconds(player, player.getItemBySlot(EquipmentSlot.OFFHAND));
        if (BackpackCompat.isLoaded()) {
            for (BackpackCompat.BackpackRing ring : BackpackCompat.findRingsInBackpacks(player)) {
                total += remainingSeconds(player, ring.stack());
            }
        }
        return total;
    }

    private static boolean hasIndestructibleRing(Player player) {
        if (CuriosCompat.isLoaded() && isIndestructibleRing(CuriosCompat.findRingInSlot(player))) {
            return true;
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (isIndestructibleRing(stack)) {
                return true;
            }
        }
        if (isIndestructibleRing(player.getItemBySlot(EquipmentSlot.OFFHAND))) {
            return true;
        }
        if (BackpackCompat.isLoaded()) {
            for (BackpackCompat.BackpackRing ring : BackpackCompat.findRingsInBackpacks(player)) {
                if (isIndestructibleRing(ring.stack())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isIndestructibleRing(ItemStack stack) {
        return stack.getItem() instanceof FlightRingItem && stack.has(ModDataComponents.INDESTRUCTIBLE.get());
    }

    private static int remainingSeconds(Player player, ItemStack stack) {
        if (isUsableRing(stack)) {
            int remainingPoints = stack.getMaxDamage() - stack.getDamageValue();
            int unbreaking = stack.getEnchantments()
                    .getLevel(player.registryAccess().holderOrThrow(Enchantments.UNBREAKING));
            return remainingPoints * (1 + unbreaking);
        }
        return 0;
    }

    private static boolean isUsableRing(ItemStack stack) {
        return stack.getItem() instanceof FlightRingItem
                && (stack.has(ModDataComponents.INDESTRUCTIBLE.get()) || stack.getDamageValue() < stack.getMaxDamage());
    }

    private FlightHud() {
    }
}
