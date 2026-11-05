package com.flightring;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

/**
 * Turns the loot table's placeholder reforge stone into a <b>randomly directed</b> one.
 * <p>
 * The table already handles the 1% through {@code minecraft:random_chance} and makes sure a single chest
 * yields at most one stone ({@code rolls: 1}); this function only picks which half the stone rerolls and
 * stamps that choice on it as the {@code reforge_mode} component.
 * <p>
 * No world-wide limit: every chest rolls its own 1% (user 2026-11-05 - a global limit would soft-lock other
 * players on a server whenever the finder logs off with the stone in their inventory).
 * <p>
 * Referenced from the loot table as {@code simpleflightring:directed_stone}.
 */
public final class DirectedStoneFunction implements LootItemFunction {

    public static final DirectedStoneFunction INSTANCE = new DirectedStoneFunction();
    public static final MapCodec<DirectedStoneFunction> CODEC = MapCodec.unit(INSTANCE);

    private DirectedStoneFunction() {
    }

    @Override
    public MapCodec<? extends LootItemFunction> codec() {
        return CODEC;
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext lootContext) {
        ReforgeMode mode = lootContext.getRandom().nextBoolean() ? ReforgeMode.PREFIX : ReforgeMode.RARITY;
        ItemStack directed = new ItemStack(ModItems.REFORGE_STONE_DIRECTED.get());
        directed.set(ModDataComponents.REFORGE_MODE.get(), mode);
        return directed;
    }
}
