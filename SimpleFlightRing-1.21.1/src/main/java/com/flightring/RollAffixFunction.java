package com.flightring;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

/**
 * Rolls a random affix onto a ring taken from a broken-dimension chest and randomises its durability.
 * <p>
 * The prefix is uniform over the 13 entries; the rarity uses the <b>chest</b> weights, so it is capped at
 * {@code LEGENDARY} ({@code SACRED} can only be rolled at an anvil). Durability lands anywhere from "almost
 * broken" to full - {@code damage in [0, maxDamage - 1]} - which is what makes a good find worth repairing
 * and keeps a plain max-durability roll possible. Rings with the {@code INDESTRUCTIBLE} component are left
 * alone (they have no durability bar at all).
 * <p>
 * Referenced from the loot table as {@code simpleflightring:roll_affix}.
 */
public final class RollAffixFunction implements LootItemFunction {

    public static final RollAffixFunction INSTANCE = new RollAffixFunction();
    public static final MapCodec<RollAffixFunction> CODEC = MapCodec.unit(INSTANCE);

    private RollAffixFunction() {
    }

    @Override
    public net.minecraft.world.level.storage.loot.functions.LootItemFunctionType<? extends LootItemFunction> getType() {
        return ModLootFunctions.ROLL_AFFIX.get();
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext lootContext) {
        if (!(stack.getItem() instanceof FlightRingItem)) {
            return stack;
        }
        RandomSource random = lootContext.getRandom();
        RingAffix.Prefix[] prefixes = RingAffix.Prefix.values();
        RingAffix.Prefix prefix = prefixes[random.nextInt(prefixes.length)];
        RingAffix.Rarity rarity = RingAffix.rollRarity(random, false);
        stack.set(ModDataComponents.RING_AFFIX.get(), new RingAffix(prefix, rarity));
        int maxDamage = stack.getMaxDamage();
        if (maxDamage > 1 && !stack.has(ModDataComponents.INDESTRUCTIBLE.get())) {
            stack.setDamageValue(random.nextInt(maxDamage));
        }
        return stack;
    }
}
