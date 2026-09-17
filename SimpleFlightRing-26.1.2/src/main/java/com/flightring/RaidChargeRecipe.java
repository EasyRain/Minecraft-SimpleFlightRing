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
 * Charging a raid ring with a totem of undying: one raid ring + one totem =
 * {@link RaidCharges#PER_TOTEM} more charges, so a totem is worth twice as much inside the ring
 * as it is on its own.
 * <p>
 * A recipe of its own instead of a vanilla one, for the two reasons that matter here: a vanilla
 * crafting recipe can never copy what is on an ingredient, and the ring must keep everything it
 * already has (its enchantments, its remaining durability, its name). It also refuses to match
 * once another totem would no longer fit - a ring at {@link RaidCharges#MAX} charges, or at nine
 * where the totem's two charges would overshoot the cap, simply produces nothing.
 * <p>
 * 26.1.2 spells this as {@link NormalCraftingRecipe} with a {@link Recipe.CommonInfo}, a
 * {@link CraftingRecipe.CraftingBookInfo}, an {@link ItemStackTemplate} result and the
 * placement / display information the recipe book and JEI read, exactly like
 * {@link RingRepairRecipe}.
 */
public class RaidChargeRecipe extends NormalCraftingRecipe {

    public static final MapCodec<RaidChargeRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
                    CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.bookInfo),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
            ).apply(instance, RaidChargeRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidChargeRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC,
            recipe -> recipe.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC,
            recipe -> recipe.bookInfo,
            ItemStackTemplate.STREAM_CODEC,
            recipe -> recipe.result,
            RaidChargeRecipe::new
    );

    public static final RecipeSerializer<RaidChargeRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ItemStackTemplate result;

    public RaidChargeRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
                            ItemStackTemplate result) {
        super(commonInfo, bookInfo);
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
            if (stack.is(this.result.item())) {
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
    public ItemStack assemble(CraftingInput input) {
        ItemStack ring = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.is(this.result.item())) {
                ring = stack;
                break;
            }
        }
        ItemStack charged = ring.copy();
        RaidCharges.set(charged, RaidCharges.of(ring) + RaidCharges.PER_TOTEM);
        return charged;
    }

    @Override
    public RecipeSerializer<? extends NormalCraftingRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(List.of(
                Optional.of(Ingredient.of(Items.TOTEM_OF_UNDYING)),
                Optional.of(Ingredient.of(this.result.item().value()))));
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(
                new ShapelessCraftingRecipeDisplay(
                        List.of(Ingredient.of(Items.TOTEM_OF_UNDYING).display(),
                                new SlotDisplay.ItemSlotDisplay(this.result.item())),
                        new SlotDisplay.ItemStackSlotDisplay(this.result),
                        new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)
                )
        );
    }
}
