package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * Keeps the flight ring tooltips short. The ring's enchantments themselves (built-in
 * and normal ones) are always listed; only the long enchantment EFFECT hints are
 * folded away - while Shift is not held a single "hold Shift" line takes their place.
 * <p>
 * Client-only: the fold state comes from the shift key, and the event fires while the
 * tooltip is gathered.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class RingTooltipHandler {

    private static final String INTRINSIC_KEY = "tooltip.simpleflightring.intrinsic_enchant";

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof FlightRingItem ring)) {
            return;
        }
        List<Component> hints = ring.effectHints(stack, event.getContext());
        if (hints.isEmpty()) {
            return; // nothing to fold away
        }
        List<Component> lines = event.getToolTip();
        // The hints sit right above the built-in enchantment lines; without built-in
        // enchantments (the tiered rings) they go last, after the normal enchantments.
        int at = firstIndexOf(lines, INTRINSIC_KEY);
        if (at < 0) {
            at = lines.size();
        }
        if (Screen.hasShiftDown()) {
            lines.addAll(at, hints);
        } else {
            lines.add(at, Component.translatable("tooltip.simpleflightring.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Index of the first line built from the given translation key, or -1 when absent. */
    private static int firstIndexOf(List<Component> lines, String translationKey) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getContents() instanceof TranslatableContents contents
                    && translationKey.equals(contents.getKey())) {
                return i;
            }
        }
        return -1;
    }

    private RingTooltipHandler() {
    }
}
