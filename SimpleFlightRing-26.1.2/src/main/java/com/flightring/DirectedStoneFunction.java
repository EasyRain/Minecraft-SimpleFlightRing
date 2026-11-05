package com.flightring;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

/**
 * Turns the loot table's placeholder reforge stone into this world's single directed stone.
 * <p>
 * The table already fails 99% of the time through {@code minecraft:random_chance}; this function then asks
 * {@link DirectedStoneData} whether the world still has its one stone. If not, the stack is emptied (the loot
 * table simply drops nothing) and the world is locked to the kind picked here.
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
        if (lootContext.getLevel().getServer() == null) {
            return ItemStack.EMPTY;
        }
        DirectedStoneData data = DirectedStoneData.get(lootContext.getLevel().getServer());
        String kind = data.claim(lootContext.getRandom());
        if (kind == null) {
            return ItemStack.EMPTY;
        }
        ReforgeMode mode = ReforgeMode.byKey(kind);
        ItemStack directed = new ItemStack(ModItems.REFORGE_STONE_DIRECTED.get());
        if (mode != null) {
            directed.set(ModDataComponents.REFORGE_MODE.get(), mode);
        }
        FlightRingMod.LOGGER.info("[FlightRing] this world's directed reforge stone ({}) has been generated", kind);
        return directed;
    }
}
