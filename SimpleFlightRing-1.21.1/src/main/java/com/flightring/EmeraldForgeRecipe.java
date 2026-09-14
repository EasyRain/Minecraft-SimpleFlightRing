package com.flightring;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

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
public class EmeraldForgeRecipe implements CraftingRecipe {

    private final ShapedRecipePattern pattern;
    private final ItemStack result;
    private final String group;
    private final CraftingBookCategory category;
    private final boolean showNotification;

    public EmeraldForgeRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
                              ItemStack result, boolean showNotification) {
        this.group = group;
        this.category = category;
        this.pattern = pattern;
        this.result = result;
        this.showNotification = showNotification;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.pattern.matches(input);
    }

    /** The forged ring, carrying the level of the damaged ring that was used up for it. */
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack assembled = this.result.copy();
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
    public boolean canCraftInDimensions(int width, int height) {
        return width >= this.pattern.width() && height >= this.pattern.height();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.pattern.ingredients();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.EMERALD_FORGE.get();
    }

    @Override
    public String getGroup() {
        return this.group;
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    @Override
    public boolean showNotification() {
        return this.showNotification;
    }

    public ShapedRecipePattern getPattern() {
        return this.pattern;
    }

    public ItemStack getResult() {
        return this.result;
    }

    public static class Serializer implements RecipeSerializer<EmeraldForgeRecipe> {

        public static final MapCodec<EmeraldForgeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(EmeraldForgeRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(EmeraldForgeRecipe::category),
                ShapedRecipePattern.MAP_CODEC.forGetter(EmeraldForgeRecipe::getPattern),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(EmeraldForgeRecipe::getResult),
                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(EmeraldForgeRecipe::showNotification)
        ).apply(instance, EmeraldForgeRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, EmeraldForgeRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<EmeraldForgeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, EmeraldForgeRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static EmeraldForgeRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
            ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            boolean showNotification = buffer.readBoolean();
            return new EmeraldForgeRecipe(group, category, pattern, result, showNotification);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, EmeraldForgeRecipe recipe) {
            buffer.writeUtf(recipe.group);
            buffer.writeEnum(recipe.category);
            ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
            buffer.writeBoolean(recipe.showNotification);
        }
    }
}
