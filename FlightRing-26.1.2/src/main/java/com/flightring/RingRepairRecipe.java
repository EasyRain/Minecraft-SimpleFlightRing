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
 * Shapeless repair recipe: one flight ring + a stack of its tier material.
 * Each material unit repairs 25% durability; a stacked input repairs several
 * units at once and only the actually needed amount is consumed. Netherite
 * repairs with a single ingot. Enchantments, custom name and lore are
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
        int materialCount = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(this.result.item())) {
                ring = stack;
            } else {
                materialCount += stack.getCount();
            }
        }
        ItemStack repaired = ring.copy();
        int consumed = this.neededCount(ring, materialCount);
        int maxDamage = ring.getMaxDamage();
        int repair = this.fullRepair ? maxDamage : (maxDamage / 4) * consumed;
        repaired.setDamageValue(Math.max(0, ring.getDamageValue() - repair));
        return repaired;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        ItemStack ring = ItemStack.EMPTY;
        int materialCount = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(this.result.item())) {
                ring = stack;
            } else {
                materialCount += stack.getCount();
            }
        }
        if (ring.isEmpty()) {
            return remaining;
        }
        int consumed = this.neededCount(ring, materialCount);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty() || stack.is(this.result.item())) {
                continue;
            }
            int left = stack.getCount() - (this.material.test(stack) ? consumed : 0);
            remaining.set(i, left > 0 ? stack.copyWithCount(left) : ItemStack.EMPTY);
        }
        return remaining;
    }

    /** How many material units this recipe will actually consume. */
    private int neededCount(ItemStack ring, int available) {
        int needed;
        if (this.fullRepair) {
            needed = 1;
        } else {
            int quarter = ring.getMaxDamage() / 4;
            needed = (ring.getDamageValue() + quarter - 1) / quarter;
        }
        return Math.min(available, needed);
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
