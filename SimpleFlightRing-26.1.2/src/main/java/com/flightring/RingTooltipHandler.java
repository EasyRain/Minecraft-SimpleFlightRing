package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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

    /**
     * The ring's own informative lines, added by {@link FlightRingItem#appendHoverText}.
     * They are always written before the vanilla enchantment lines (which
     * {@code ItemStack.getTooltipLines} appends after {@code appendHoverText}), so the
     * folded block goes right after the last of them when the ring has no built-in
     * enchantments to anchor on.
     */
    private static final List<String> INFO_KEYS = List.of(
            "tooltip.simpleflightring.remaining_infinite",
            "tooltip.simpleflightring.remaining_time",
            "tooltip.simpleflightring.remaining_time_long",
            "tooltip.simpleflightring.breaks_when_depleted");

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
        // Rings with built-in enchantments: the block sits right above their "Built-in ..." line.
        // The other rings have no such anchor, so it goes right after their own info lines -
        // never at the very end, which would put it below the enchantment lines.
        int at = firstIndexOf(lines, INTRINSIC_KEY);
        if (at < 0) {
            at = afterLastIndexOf(lines, INFO_KEYS);
        }
        if (Minecraft.getInstance().hasShiftDown()) {
            lines.addAll(at, hints);
        } else {
            lines.add(at, Component.translatable("tooltip.simpleflightring.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Index of the first line built from the given translation key, or -1 when absent. */
    private static int firstIndexOf(List<Component> lines, String translationKey) {
        for (int i = 0; i < lines.size(); i++) {
            if (hasKey(lines.get(i), translationKey)) {
                return i;
            }
        }
        return -1;
    }

    /** Index just after the last line built from one of the given translation keys. */
    private static int afterLastIndexOf(List<Component> lines, List<String> translationKeys) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            for (String key : translationKeys) {
                if (hasKey(lines.get(i), key)) {
                    return i + 1;
                }
            }
        }
        // No info line at all: stay right below the item name.
        return lines.isEmpty() ? 0 : 1;
    }

    private static boolean hasKey(Component line, String translationKey) {
        return line.getContents() instanceof TranslatableContents contents
                && translationKey.equals(contents.getKey());
    }

    private RingTooltipHandler() {
    }
}
