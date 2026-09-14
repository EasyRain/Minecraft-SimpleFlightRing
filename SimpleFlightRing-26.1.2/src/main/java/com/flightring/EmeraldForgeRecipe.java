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
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Forging the emerald ring: a 3x3 shaped recipe (8 emeralds around the awakened damaged ring)
 * that hands the ring's strength over to the forged one.
 * <p>
 * It has to be a recipe of its own instead of a vanilla {@code minecraft:crafting_shaped} one,
 * for two reasons: a vanilla crafting recipe can never copy a component from an ingredient, and
 * the crafting table builds the stack shown in the result slot by calling {@link #assemble} -
 * so with a vanilla recipe the preview sat there at level I until the ring was actually taken
 * out. Reading the damaged ring from the input here gives the right level in the preview and in
 * the crafted ring, with no post-craft fixup anywhere.
 */
public class EmeraldForgeRecipe extends NormalCraftingRecipe {

    public static final MapCodec<EmeraldForgeRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
                    CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.bookInfo),
                    ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
            ).apply(instance, EmeraldForgeRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, EmeraldForgeRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC,
            recipe -> recipe.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC,
            recipe -> recipe.bookInfo,
            ShapedRecipePattern.STREAM_CODEC,
            recipe -> recipe.pattern,
            ItemStackTemplate.STREAM_CODEC,
            recipe -> recipe.result,
            EmeraldForgeRecipe::new
    );

    public static final RecipeSerializer<EmeraldForgeRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ShapedRecipePattern pattern;
    private final ItemStackTemplate result;

    public EmeraldForgeRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
                              ShapedRecipePattern pattern, ItemStackTemplate result) {
        super(commonInfo, bookInfo);
        this.pattern = pattern;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.pattern.matches(input);
    }

    /** The forged ring, carrying the level of the damaged ring that was used up for it. */
    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack assembled = this.result.create();
        for (ItemStack stack : input.items()) {
            if (stack.getItem() instanceof DamagedRingItem damaged
                    && damaged.relic() == RelicRing.EMERALD) {
                HeroLevel.set(assembled, HeroLevel.of(stack));
                break;
            }
        }
        return assembled;
    }

    @Override
    public RecipeSerializer<? extends NormalCraftingRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(this.pattern.ingredients());
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(
                new ShapedCraftingRecipeDisplay(
                        this.pattern.width(),
                        this.pattern.height(),
                        this.pattern.ingredients().stream()
                                .map(e -> e.map(Ingredient::display).orElse(SlotDisplay.Empty.INSTANCE))
                                .toList(),
                        new SlotDisplay.ItemStackSlotDisplay(this.result),
                        new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)
                )
        );
    }
}
