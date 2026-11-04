package com.flightring;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Loot functions added by this mod. The broken dimension's chest loot uses {@link RollAffixFunction} to put
 * a random affix on the ring it drops.
 */
public class ModLootFunctions {

    public static final DeferredRegister<LootItemFunctionType<?>> LOOT_FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, FlightRingMod.MODID);

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<RollAffixFunction>> ROLL_AFFIX =
            LOOT_FUNCTIONS.register("roll_affix", () -> new LootItemFunctionType<>(RollAffixFunction.CODEC));
}
