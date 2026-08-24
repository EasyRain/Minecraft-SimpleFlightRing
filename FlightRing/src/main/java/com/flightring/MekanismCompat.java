package com.flightring;

import mekanism.api.gear.IModuleHelper;
import mekanism.common.registries.MekanismModules;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Optional Mekanism integration (1.21.1): the MekaSuit leggings module
 * "Gyroscopic Stabilization Unit" already cancels the mid-air mining
 * slow-down. Flight Stability must not apply its own handling in that case,
 * otherwise both effects stack and mining becomes much faster than intended.
 */
public class MekanismCompat {

    public static boolean isLoaded() {
        return net.neoforged.fml.ModList.get().isLoaded("mekanism");
    }

    /** True if the player wears MekaSuit leggings with the Gyroscopic Stabilization Unit enabled. */
    public static boolean hasGyroscopicStabilizer(Player player) {
        if (!isLoaded()) {
            return false;
        }
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        return !legs.isEmpty() && IModuleHelper.INSTANCE.isEnabled(legs, MekanismModules.GYROSCOPIC_STABILIZATION_UNIT);
    }

    private MekanismCompat() {
    }
}
