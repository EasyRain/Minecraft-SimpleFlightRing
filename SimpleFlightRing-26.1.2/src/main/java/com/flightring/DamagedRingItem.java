package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * The broken form of a relic ring ({@link RelicRing}). Exactly this item is what the
 * structure loot tables hand out and what a master librarian sells.
 * <p>
 * It is deliberately NOT a {@link FlightRingItem}: it cannot fly, cannot be worn in the
 * Curios slot and has no durability bar. Repairing it in the crafting table together with
 * the ring's theme material yields the working ring (see the {@code repair_damaged_*}
 * recipes).
 */
public class DamagedRingItem extends Item {

    private final RelicRing relic;

    public DamagedRingItem(RelicRing relic, Properties properties) {
        super(properties);
        this.relic = relic;
    }

    /** The relic ring this broken item repairs into. */
    public RelicRing relic() {
        return relic;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.damaged").withStyle(ChatFormatting.RED));
        tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.damaged_repair",
                new ItemStack(relic.repairMaterial()).getHoverName()).withStyle(ChatFormatting.GRAY));
    }
}
