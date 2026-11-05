package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * "Direct one + plain one = two direct ones" - the copy recipe for the directed reforge stone.
 * <p>
 * It has to be a custom recipe (instead of a data-driven shapeless one) because the result must inherit the
 * <b>mode component</b> of the input stone: a data recipe can only produce a fixed stack, which would turn a
 * rarity stone into a prefix one.
 * <p>
 * Shapeless on purpose, so either stone may sit in any slot and the whole thing fits a 2x2 grid.
 */
public class DirectedStoneCopyRecipe extends ShapelessRecipe {

    public DirectedStoneCopyRecipe(String group, CraftingBookCategory category, Ingredient directed,
                                   Ingredient plain, ItemStack result) {
        super(group, category, result, NonNullList.of(Ingredient.EMPTY, directed, plain));
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean directed = false;
        boolean plain = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.REFORGE_STONE_DIRECTED.get())) {
                if (directed) {
                    return false;
                }
                directed = true;
            } else if (stack.is(ModItems.REFORGE_STONE.get())) {
                if (plain) {
                    return false;
                }
                plain = true;
            } else {
                return false;
            }
        }
        return directed && plain;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack template = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.is(ModItems.REFORGE_STONE_DIRECTED.get())) {
                template = stack;
                break;
            }
        }
        ItemStack out = new ItemStack(ModItems.REFORGE_STONE_DIRECTED.get(), 2);
        // Carry the mode over, so rarirty stones copy into rarity stones and prefix into prefix.
        ReforgeMode mode = template.get(ModDataComponents.REFORGE_MODE.get());
        if (mode != null) {
            out.set(ModDataComponents.REFORGE_MODE.get(), mode);
        }
        return out;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.DIRECTED_STONE_COPY.get();
    }

    public static class Serializer implements RecipeSerializer<DirectedStoneCopyRecipe> {

        public static final MapCodec<DirectedStoneCopyRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                com.mojang.serialization.Codec.STRING.optionalFieldOf("group", "").forGetter(recipe -> recipe.getGroup()),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(DirectedStoneCopyRecipe::category),
                Ingredient.CODEC.fieldOf("directed").forGetter(recipe -> Ingredient.of(ModItems.REFORGE_STONE_DIRECTED.get())),
                Ingredient.CODEC.fieldOf("plain").forGetter(recipe -> Ingredient.of(ModItems.REFORGE_STONE.get())),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> new ItemStack(ModItems.REFORGE_STONE_DIRECTED.get(), 2))
        ).apply(instance, DirectedStoneCopyRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, DirectedStoneCopyRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<DirectedStoneCopyRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DirectedStoneCopyRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static DirectedStoneCopyRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
            Ingredient directed = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            Ingredient plain = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            return new DirectedStoneCopyRecipe(group, category, directed, plain, result);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, DirectedStoneCopyRecipe recipe) {
            buffer.writeUtf(recipe.getGroup());
            buffer.writeEnum(recipe.category());
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, Ingredient.of(ModItems.REFORGE_STONE_DIRECTED.get()));
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, Ingredient.of(ModItems.REFORGE_STONE.get()));
            ItemStack.STREAM_CODEC.encode(buffer, new ItemStack(ModItems.REFORGE_STONE_DIRECTED.get(), 2));
        }
    }
}
