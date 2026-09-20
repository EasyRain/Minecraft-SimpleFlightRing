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
 *   <li><b>Emerald</b>: win a raid while carrying it, then add 8 emeralds
 *       (see {@link EmeraldRingQuest}).</li>
 *   <li><b>Ocean</b>: slay an Elder Guardian while carrying it, then forge the vessel out of a
 *       heart of the sea, prismarine shards and a nautilus shell (see {@link OceanRingQuest}).</li>
 *   <li><b>Raid</b>: kill three iron golems within sixty seconds while carrying it, then bind a
 *       totem of undying into it (see {@link RaidRingQuest}). The finished ring works as a totem
 *       itself, holding up to {@link RaidCharges#MAX} charges, two per totem.</li>
 *   <li><b>Desert</b>: no quest at all - the ring is only weathered, so a plain polish of gold
 *       ingots and lapis lazuli around it is enough (the {@code repair_damaged_desert_flight_ring}
 *       recipe).</li>
 *   <li><b>Infernal</b>: kill a Wither while carrying it, then throw it into lava, which quenches
 *       it into the working ring (see {@link InfernalRingQuest}).</li>
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

    /**
     * True once the emerald ring has been carried through a won raid
     * (see {@link EmeraldRingQuest}).
     */
    public static boolean isHeroCharged(ItemStack stack) {
        return stack.has(ModDataComponents.HERO_CHARGED.get());
    }

    /** True once the sleeping guardian acknowledged the ocean ring (see {@link OceanRingQuest}). */
    public static boolean isGuardianAcknowledged(ItemStack stack) {
        return stack.has(ModDataComponents.GUARDIAN_ACKNOWLEDGED.get());
    }

    /**
     * True once the illager will accepted the raid ring's trial - three iron golems inside sixty
     * seconds (see {@link RaidRingQuest}).
     */
    public static boolean isIllagerAcknowledged(ItemStack stack) {
        return stack.has(ModDataComponents.ILLAGER_ACKNOWLEDGED.get());
    }

    /**
     * True once the infernal ring swallowed a Wither's death flame - killing a Wither while
     * carrying it (see {@link InfernalRingQuest}). That is what lets the lava quench it.
     */
    public static boolean isWitherCharged(ItemStack stack) {
        return stack.has(ModDataComponents.WITHER_CHARGED.get());
    }

    /** A broken ring glints as soon as its quest step is done and only material is missing. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return hasWardenSoul(stack) || isBlastForged(stack) || isHeroCharged(stack)
                || isGuardianAcknowledged(stack) || isIllagerAcknowledged(stack)
                || isWitherCharged(stack) || super.isFoil(stack);
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
            // The emerald ring's quest text: three grey flavour lines, then what to do next.
            // Charged rings swap that last line for the state and the missing vessel.
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.emerald_damaged_gone")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.emerald_damaged_name")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.emerald_damaged_deeds")
                    .withStyle(ChatFormatting.GRAY));
            if (isHeroCharged(stack)) {
                // The light it remembers is the level of the raid it was carried through.
                tooltipComponents.add(Component.translatable("tooltip.simpleflightring.emerald_damaged_charged",
                        HeroLevel.roman(HeroLevel.of(stack))).withStyle(ChatFormatting.WHITE));
                tooltipComponents.add(Component.translatable("tooltip.simpleflightring.emerald_damaged_vessel")
                        .withStyle(ChatFormatting.WHITE));
            } else {
                tooltipComponents.add(Component.translatable("tooltip.simpleflightring.emerald_damaged_wake")
                        .withStyle(ChatFormatting.WHITE));
            }
            return;
        }
        if (relic == RelicRing.OCEAN) {
            // The ocean ring's quest text: what the sea took, then what to do next. The second
            // and third line carry the ability's own colour and change once the guardian acknowledges it.
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.ocean_damaged_eroded")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.translatable(isGuardianAcknowledged(stack)
                            ? "tooltip.simpleflightring.ocean_damaged_acknowledged"
                            : "tooltip.simpleflightring.ocean_damaged_waiting")
                    .withColor(RingAbility.OCEAN_FAVORED.color()));
            tooltipComponents.add(Component.translatable(isGuardianAcknowledged(stack)
                            ? "tooltip.simpleflightring.ocean_damaged_restore"
                            : "tooltip.simpleflightring.ocean_damaged_prove")
                    .withColor(RingAbility.OCEAN_FAVORED.color()));
            return;
        }
        if (relic == RelicRing.RAID) {
            // The raid ring's quest text: two grey flavour lines, then the state of the trial in
            // the ability's own colour. The trial is three iron golems inside sixty seconds.
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.raid_damaged_lost")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.raid_damaged_will")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.translatable(isIllagerAcknowledged(stack)
                            ? "tooltip.simpleflightring.raid_damaged_acknowledged"
                            : "tooltip.simpleflightring.raid_damaged_prove")
                    .withColor(RingAbility.RAID_PLUNDER.color()));
            return;
        }
        if (relic == RelicRing.DESERT) {
            // The desert ring is the one relic with no quest at all: it is merely weathered, so
            // its three lines never change and a plain polish of gold and lapis lazuli is enough.
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.desert_damaged_history")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.desert_damaged_rusted")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.desert_damaged_polish")
                    .withColor(RingAbility.DESERT_GUIDE.color()));
            return;
        }
        if (relic == RelicRing.INFERNAL) {
            // The infernal ring's quest text: what its fire is waiting for, where the ring stands
            // right now, then the missing step - the last two swap once the Wither's death flame
            // is inside it. The fourth line is the ring's own promise: lava cannot destroy it.
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.infernal_damaged_asleep")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.translatable(isWitherCharged(stack)
                            ? "tooltip.simpleflightring.infernal_damaged_ember"
                            : "tooltip.simpleflightring.infernal_damaged_smothered")
                    .withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.translatable(isWitherCharged(stack)
                            ? "tooltip.simpleflightring.infernal_damaged_quench"
                            : "tooltip.simpleflightring.infernal_damaged_slay_wither")
                    .withColor(RingAbility.FLAME_LORD.color()));
            tooltipComponents.add(Component.translatable("tooltip.simpleflightring.infernal_damaged_lava")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltipComponents.add(Component.translatable("tooltip.simpleflightring.damaged").withStyle(ChatFormatting.RED));
        tooltipComponents.add(Component.translatable("tooltip.simpleflightring.damaged_repair",
                new ItemStack(relic.repairMaterial()).getHoverName()).withStyle(ChatFormatting.GRAY));
    }
}
