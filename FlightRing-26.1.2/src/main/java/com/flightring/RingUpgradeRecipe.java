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
 * Crafting-table tier upgrade recipe: a 3x3 shaped recipe whose center is the
 * lower-tier flight ring. The ring input may have any durability (including a
 * fully consumed one); enchantments, custom name and lore are carried over to
 * the fresh next-tier ring.
 */
public class RingUpgradeRecipe extends NormalCraftingRecipe {

    public static final MapCodec<RingUpgradeRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(ring -> ring.commonInfo),
                    CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(ring -> ring.bookInfo),
                    ShapedRecipePattern.MAP_CODEC.forGetter(ring -> ring.pattern),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(ring -> ring.result)
            ).apply(instance, RingUpgradeRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RingUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC,
            ring -> ring.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC,
            ring -> ring.bookInfo,
            ShapedRecipePattern.STREAM_CODEC,
            ring -> ring.pattern,
            ItemStackTemplate.STREAM_CODEC,
            ring -> ring.result,
            RingUpgradeRecipe::new
    );

    public static final RecipeSerializer<RingUpgradeRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ShapedRecipePattern pattern;
    private final ItemStackTemplate result;

    public RingUpgradeRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
                             ShapedRecipePattern pattern, ItemStackTemplate result) {
        super(commonInfo, bookInfo);
        this.pattern = pattern;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.pattern.matches(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack assembled = this.result.create();
        for (ItemStack stack : input.items()) {
            if (stack.getItem() instanceof FlightRingItem) {
                RingUpgradeHelper.copyComponents(stack, assembled);
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
