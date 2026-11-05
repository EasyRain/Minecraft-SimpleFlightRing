package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * "Direct one + plain one = two direct ones" - the copy recipe for the directed reforge stone.
 * <p>
 * It has to be a custom recipe (instead of a data-driven shapeless one) because the result must inherit the
 * <b>mode component</b> of the input stone: a data recipe can only produce a fixed stack, which would turn a
 * rarity stone into a prefix one.
 * <p>
 * Shapeless on purpose, so either stone may sit in any slot and the whole thing fits a 2x2 grid.
 */
public class DirectedStoneCopyRecipe extends NormalCraftingRecipe {

    public static final MapCodec<DirectedStoneCopyRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
                    CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.bookInfo),
                    Ingredient.CODEC.fieldOf("directed").forGetter(recipe -> recipe.directed),
                    Ingredient.CODEC.fieldOf("plain").forGetter(recipe -> recipe.plain),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
            ).apply(instance, DirectedStoneCopyRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, DirectedStoneCopyRecipe> STREAM_CODEC =
            StreamCodec.of(DirectedStoneCopyRecipe::toNetwork, DirectedStoneCopyRecipe::fromNetwork);

    public static final RecipeSerializer<DirectedStoneCopyRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final Ingredient directed;
    private final Ingredient plain;
    private final ItemStackTemplate result;

    public DirectedStoneCopyRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
                                   Ingredient directed, Ingredient plain, ItemStackTemplate result) {
        super(commonInfo, bookInfo);
        this.directed = directed;
        this.plain = plain;
        this.result = result;
    }

    private static DirectedStoneCopyRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
        Recipe.CommonInfo common = Recipe.CommonInfo.STREAM_CODEC.decode(buffer);
        CraftingRecipe.CraftingBookInfo book = CraftingRecipe.CraftingBookInfo.STREAM_CODEC.decode(buffer);
        Ingredient directed = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
        Ingredient plain = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
        ItemStackTemplate result = ItemStackTemplate.STREAM_CODEC.decode(buffer);
        return new DirectedStoneCopyRecipe(common, book, directed, plain, result);
    }

    private static void toNetwork(RegistryFriendlyByteBuf buffer, DirectedStoneCopyRecipe recipe) {
        Recipe.CommonInfo.STREAM_CODEC.encode(buffer, recipe.commonInfo);
        CraftingRecipe.CraftingBookInfo.STREAM_CODEC.encode(buffer, recipe.bookInfo);
        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.directed);
        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.plain);
        ItemStackTemplate.STREAM_CODEC.encode(buffer, recipe.result);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean foundDirected = false;
        boolean foundPlain = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.REFORGE_STONE_DIRECTED.get())) {
                if (foundDirected) {
                    return false;
                }
                foundDirected = true;
            } else if (stack.is(ModItems.REFORGE_STONE.get())) {
                if (foundPlain) {
                    return false;
                }
                foundPlain = true;
            } else {
                return false;
            }
        }
        return foundDirected && foundPlain;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack template = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.is(ModItems.REFORGE_STONE_DIRECTED.get())) {
                template = stack;
                break;
            }
        }
        ItemStack out = new ItemStack(ModItems.REFORGE_STONE_DIRECTED.get(), 2);
        // Carry the mode over, so rarity stones copy into rarity stones and prefix into prefix.
        ReforgeMode mode = template.get(ModDataComponents.REFORGE_MODE.get());
        if (mode != null) {
            out.set(ModDataComponents.REFORGE_MODE.get(), mode);
        }
        return out;
    }

    @Override
    public RecipeSerializer<? extends NormalCraftingRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(
                List.of(Optional.of(this.directed), Optional.of(this.plain)));
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(
                new ShapelessCraftingRecipeDisplay(
                        List.of(this.directed.display(), this.plain.display()),
                        new SlotDisplay.ItemStackSlotDisplay(this.result),
                        new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)
                )
        );
    }
}
