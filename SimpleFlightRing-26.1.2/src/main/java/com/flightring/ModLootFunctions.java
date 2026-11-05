package com.flightring;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Loot functions added by this mod. The broken dimension's chest loot uses {@link RollAffixFunction} to put
 * a random affix on the ring it drops.
 * <p>
 * 26.x registers the {@code MapCodec} itself: {@code Registries.LOOT_FUNCTION_TYPE} is a
 * {@code Registry<MapCodec<? extends LootItemFunction>>} and the old {@code LootItemFunctionType} wrapper no
 * longer exists ({@code LootItemFunction#getType()} became {@code #codec()}). 1.21.1 keeps the wrapper, so
 * this class differs between the two versions on purpose.
 */
public class ModLootFunctions {

    public static final DeferredRegister<MapCodec<? extends LootItemFunction>> LOOT_FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, FlightRingMod.MODID);

    public static final DeferredHolder<MapCodec<? extends LootItemFunction>, MapCodec<RollAffixFunction>> ROLL_AFFIX =
            LOOT_FUNCTIONS.register("roll_affix", () -> RollAffixFunction.CODEC);

    /** Hands out this world's single directed reforge stone (see {@link DirectedStoneFunction}). */
    public static final DeferredHolder<MapCodec<? extends LootItemFunction>, MapCodec<DirectedStoneFunction>> DIRECTED_STONE =
            LOOT_FUNCTIONS.register("directed_stone", () -> DirectedStoneFunction.CODEC);
}
