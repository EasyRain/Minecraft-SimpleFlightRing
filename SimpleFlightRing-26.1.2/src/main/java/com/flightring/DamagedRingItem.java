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
 * <p>
 * The sculk ring is special: it has its own two-step quest instead of the plain repair
 * recipe - see {@link SculkRingQuest} - so its tooltip shows the quest state and it glints
 * once the Warden's soul is inside it.
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

    /** True once the sculk ring has absorbed a Warden's soul (see {@link SculkRingQuest}). */
    public static boolean hasWardenSoul(ItemStack stack) {
        return stack.has(ModDataComponents.WARDEN_SOUL.get());
    }

    /** The broken sculk ring glints while the Warden's soul is inside it. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return hasWardenSoul(stack) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (relic == RelicRing.SCULK) {
            // The sculk ring's quest text: what it lost, then what it wants next.
            tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.sculk_lost_power")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.accept(Component.translatable(hasWardenSoul(stack)
                            ? "tooltip.simpleflightring.sculk_needs_vessel"
                            : "tooltip.simpleflightring.sculk_hungers")
                    .withColor(relic.color()));
            return;
        }
        tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.damaged").withStyle(ChatFormatting.RED));
        tooltipComponents.accept(Component.translatable("tooltip.simpleflightring.damaged_repair",
                new ItemStack(relic.repairMaterial()).getHoverName()).withStyle(ChatFormatting.GRAY));
    }
}
