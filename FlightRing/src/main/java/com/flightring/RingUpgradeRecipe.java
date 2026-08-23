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
 * Crafting-table tier upgrade recipe: a 3x3 shaped recipe whose center is the
 * lower-tier flight ring. The ring input may have any durability (including a
 * fully consumed one); enchantments, custom name and lore are carried over to
 * the fresh next-tier ring.
 */
public class RingUpgradeRecipe implements CraftingRecipe {

    private final ShapedRecipePattern pattern;
    private final ItemStack result;
    private final String group;
    private final CraftingBookCategory category;
    private final boolean showNotification;

    public RingUpgradeRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
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

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack assembled = this.result.copy();
        for (ItemStack stack : input.items()) {
            if (stack.getItem() instanceof FlightRingItem) {
                RingUpgradeHelper.copyComponents(stack, assembled);
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
        return ModRecipeSerializers.RING_UPGRADE.get();
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

    public static class Serializer implements RecipeSerializer<RingUpgradeRecipe> {

        public static final MapCodec<RingUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(RingUpgradeRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(RingUpgradeRecipe::category),
                ShapedRecipePattern.MAP_CODEC.forGetter(RingUpgradeRecipe::getPattern),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(RingUpgradeRecipe::getResult),
                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(RingUpgradeRecipe::showNotification)
        ).apply(instance, RingUpgradeRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, RingUpgradeRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<RingUpgradeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, RingUpgradeRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static RingUpgradeRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
            ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            boolean showNotification = buffer.readBoolean();
            return new RingUpgradeRecipe(group, category, pattern, result, showNotification);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, RingUpgradeRecipe recipe) {
            buffer.writeUtf(recipe.group);
            buffer.writeEnum(recipe.category);
            ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
            buffer.writeBoolean(recipe.showNotification);
        }
    }
}
