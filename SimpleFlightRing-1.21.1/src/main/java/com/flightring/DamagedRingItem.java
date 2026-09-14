package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The broken form of a relic ring ({@link RelicRing}). Exactly this item is what the
 * structure loot tables hand out and what a master librarian sells.
 * <p>
 * It is deliberately NOT a {@link FlightRingItem}: it cannot fly, cannot be worn in the
 * Curios slot and has no durability bar. Repairing it yields the working ring, but the way
 * there differs per relic:
 * <ul>
 *   <li><b>Sculk</b>: kill a Warden while carrying it, then forge it with 8 echo shards
 *       (see {@link SculkRingQuest}).</li>
 *   <li><b>Miner</b>: throw it into a blast, then add 8 iron ingots
 *       (see {@link MinerRingQuest}).</li>
 *   <li><b>Everything else</b>: the plain shapeless repair recipe with the theme material.</li>
 * </ul>
 * While a relic has its own quest its tooltip shows the quest text (and it glints once the
 * ring is ready for the final recipe) instead of the generic "damaged" lines.
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

    /** True once the miner ring has been reforged by a blast (see {@link MinerRingQuest}). */
    public static boolean isBlastForged(ItemStack stack) {
        return stack.has(ModDataComponents.BLAST_FORGED.get());
    }

    /** A broken ring glints as soon as its quest step is done and only material is missing. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return hasWardenSoul(stack) || isBlastForged(stack) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (relic == RelicRing.SCULK) {
            // The sculk ring's quest text: what it lost, then what it wants next.
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.sculk_lost_power")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.translatable(hasWardenSoul(stack)
                            ? "tooltip.simpleflightring.sculk_needs_vessel"
                            : "tooltip.simpleflightring.sculk_hungers")
                    .withColor(relic.color()));
            return;
        }
        if (relic == RelicRing.MINER) {
            // The miner ring's quest text: two grey flavour lines, then what to do next.
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.miner_damaged_origin")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.miner_damaged_shape")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.translatable(isBlastForged(stack)
                            ? "tooltip.simpleflightring.miner_damaged_ready"
                            : "tooltip.simpleflightring.miner_damaged_reforge")
                    .withStyle(ChatFormatting.WHITE));
            return;
        }
        if (relic == RelicRing.EMERALD) {
            // The only relic that cannot be looted: a master librarian may sell it.
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.emerald_damaged_source")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.damaged_repair",
                    new ItemStack(relic.repairMaterial()).getHoverName()).withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltipComponents.add(Component.translatable("tooltip.simpleflightring.damaged").withStyle(ChatFormatting.RED));
        tooltipComponents.add(Component.translatable("tooltip.simpleflightring.damaged_repair",
                new ItemStack(relic.repairMaterial()).getHoverName()).withStyle(ChatFormatting.GRAY));
    }
}
