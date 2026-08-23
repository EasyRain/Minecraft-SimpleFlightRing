package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
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
 * Shapeless repair recipe: one flight ring + 1 material unit = one craft.
 * Each craft restores 25% durability (netherite restores 100% with a single
 * ingot); repeat the craft to fully repair a ring. Consumes exactly one
 * material per craft - the standard vanilla per-slot consumption - so the
 * recipe works identically in every crafting system (crafting table, recipe
 * book, AE2 crafting terminal, ...). Enchantments, custom name and lore are
 * preserved by copying the input ring. A fully-durable ring cannot be used.
 */
public class RingRepairRecipe extends NormalCraftingRecipe {

    public static final MapCodec<RingRepairRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(ring -> ring.commonInfo),
                    CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(ring -> ring.bookInfo),
                    Ingredient.CODEC.fieldOf("material").forGetter(ring -> ring.material),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(ring -> ring.result)
            ).apply(instance, RingRepairRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RingRepairRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC,
            ring -> ring.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC,
            ring -> ring.bookInfo,
            Ingredient.CONTENTS_STREAM_CODEC,
            ring -> ring.material,
            ItemStackTemplate.STREAM_CODEC,
            ring -> ring.result,
            RingRepairRecipe::new
    );

    public static final RecipeSerializer<RingRepairRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final Ingredient material;
    private final ItemStackTemplate result;
    private final boolean fullRepair;

    public RingRepairRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
                            Ingredient material, ItemStackTemplate result) {
        super(commonInfo, bookInfo);
        this.material = material;
        this.result = result;
        this.fullRepair = result.item().is(ModItems.NETHERITE_FLIGHT_RING);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack ring = ItemStack.EMPTY;
        boolean hasMaterial = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(this.result.item())) {
                if (!ring.isEmpty()) {
                    return false;
                }
                ring = stack;
            } else if (this.material.test(stack)) {
                hasMaterial = true;
            } else {
                return false;
            }
        }
        return !ring.isEmpty() && hasMaterial && ring.getDamageValue() > 0;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack ring = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.is(this.result.item())) {
                ring = stack;
                break;
            }
        }
        ItemStack repaired = ring.copy();
        int repair = this.fullRepair ? ring.getMaxDamage() : ring.getMaxDamage() / 4;
        repaired.setDamageValue(Math.max(0, ring.getDamageValue() - repair));
        return repaired;
    }

    @Override
    public RecipeSerializer<? extends NormalCraftingRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(
                List.of(Optional.of(this.material), Optional.of(Ingredient.of(this.result.item().value()))));
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(
                new ShapelessCraftingRecipeDisplay(
                        List.of(this.material.display(), new SlotDisplay.ItemSlotDisplay(this.result.item())),
                        new SlotDisplay.ItemStackSlotDisplay(this.result),
                        new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)
                )
        );
    }
}
