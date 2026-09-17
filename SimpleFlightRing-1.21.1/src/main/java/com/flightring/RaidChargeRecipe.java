package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/**
 * Charging a raid ring with a totem of undying: one raid ring + one totem =
 * {@link RaidCharges#PER_TOTEM} more charges, so a totem is worth twice as much inside the ring
 * as it is on its own.
 * <p>
 * A recipe of its own instead of a vanilla one, for the two reasons that matter here: a vanilla
 * crafting recipe can never copy what is on an ingredient, and the ring must keep everything it
 * already has (its enchantments, its remaining durability, its name). It also refuses to match
 * once another totem would no longer fit - a ring at {@link RaidCharges#MAX} charges, or at nine
 * where the totem's two charges would overshoot the cap, simply produces nothing.
 */
public class RaidChargeRecipe extends ShapelessRecipe {

    private final ItemStack result;

    public RaidChargeRecipe(String group, CraftingBookCategory category, ItemStack result) {
        super(group, category, result, NonNullList.of(Ingredient.EMPTY,
                Ingredient.of(Items.TOTEM_OF_UNDYING), Ingredient.of(result.getItem())));
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack ring = ItemStack.EMPTY;
        boolean hasTotem = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(this.result.getItem())) {
                if (!ring.isEmpty()) {
                    return false;
                }
                ring = stack;
            } else if (stack.is(Items.TOTEM_OF_UNDYING)) {
                hasTotem = true;
            } else {
                return false;
            }
        }
        return !ring.isEmpty() && hasTotem && RaidCharges.canTakeTotem(ring);
    }

    /** The charged ring: the one that went in, keeping everything, with two more charges. */
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack ring = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.is(this.result.getItem())) {
                ring = stack;
                break;
            }
        }
        ItemStack charged = ring.copy();
        RaidCharges.set(charged, RaidCharges.of(ring) + RaidCharges.PER_TOTEM);
        return charged;
    }

    public ItemStack getResult() {
        return this.result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.RAID_CHARGE.get();
    }

    public static class Serializer implements RecipeSerializer<RaidChargeRecipe> {

        public static final MapCodec<RaidChargeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                com.mojang.serialization.Codec.STRING.optionalFieldOf("group", "").forGetter(RaidChargeRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(RaidChargeRecipe::category),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(RaidChargeRecipe::getResult)
        ).apply(instance, RaidChargeRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, RaidChargeRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<RaidChargeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, RaidChargeRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static RaidChargeRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            return new RaidChargeRecipe(group, category, result);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, RaidChargeRecipe recipe) {
            buffer.writeUtf(recipe.getGroup());
            buffer.writeEnum(recipe.category());
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
        }
    }
}
