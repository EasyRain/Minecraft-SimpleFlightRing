package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Shapeless upgrade for the Powered Flight Ring: ring + gunpowder + redstone dust.
 * Raises each of the ring's BUILT-IN enchantments (Rocket Boost and Efficiency) by one
 * level, capped at 3 - an enchantment already at (or above) the cap is left untouched,
 * so levels added with an anvil are respected. No other enchantment is ever modified.
 * The recipe does not match when nothing can be raised (both already at the cap).
 */
public class RingEnchantUpgradeRecipe extends NormalCraftingRecipe {

    /** Highest level the built-in enchantments can be raised to. */
    public static final int MAX_LEVEL = 3;

    public static final MapCodec<RingEnchantUpgradeRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
                    CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.bookInfo),
                    Ingredient.CODEC.fieldOf("ring").forGetter(recipe -> recipe.ring),
                    Ingredient.CODEC.fieldOf("catalyst").forGetter(recipe -> recipe.catalyst),
                    Ingredient.CODEC.fieldOf("binder").forGetter(recipe -> recipe.binder),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
            ).apply(instance, RingEnchantUpgradeRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RingEnchantUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, recipe -> recipe.bookInfo,
            Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.ring,
            Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.catalyst,
            Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.binder,
            ItemStackTemplate.STREAM_CODEC, recipe -> recipe.result,
            RingEnchantUpgradeRecipe::new
    );

    public static final RecipeSerializer<RingEnchantUpgradeRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final Ingredient ring;
    private final Ingredient catalyst;
    private final Ingredient binder;
    private final ItemStackTemplate result;

    public RingEnchantUpgradeRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
                                    Ingredient ring, Ingredient catalyst, Ingredient binder, ItemStackTemplate result) {
        super(commonInfo, bookInfo);
        this.ring = ring;
        this.catalyst = catalyst;
        this.binder = binder;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack found = ItemStack.EMPTY;
        int catalysts = 0;
        int binders = 0;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (this.ring.test(stack)) {
                if (!found.isEmpty()) {
                    return false;
                }
                found = stack;
            } else if (this.catalyst.test(stack)) {
                catalysts++;
            } else if (this.binder.test(stack)) {
                binders++;
            } else {
                return false;
            }
        }
        if (found.isEmpty() || catalysts != 1 || binders != 1) {
            return false;
        }
        return canRaise(found, ModEnchantments.ROCKET_BOOST)
                || canRaise(found, Enchantments.EFFICIENCY);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack ringStack = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (this.ring.test(stack)) {
                ringStack = stack;
                break;
            }
        }
        ItemStack upgraded = ringStack.copy();
        raise(upgraded, ModEnchantments.ROCKET_BOOST);
        raise(upgraded, Enchantments.EFFICIENCY);
        return upgraded;
    }

    /** True when the ring carries this built-in enchantment and it is still below the cap. */
    private static boolean canRaise(ItemStack stack, ResourceKey<Enchantment> key) {
        if (!(stack.getItem() instanceof FlightRingItem ring) || !ring.getIntrinsicLevels(stack).containsKey(key)) {
            return false;
        }
        return effectiveLevel(stack, key) < MAX_LEVEL;
    }

    /** Raises one built-in enchantment by a level (never above the cap). */
    private static void raise(ItemStack stack, ResourceKey<Enchantment> key) {
        int effective = effectiveLevel(stack, key);
        if (effective < MAX_LEVEL) {
            IntrinsicEnchants stored = stack.getOrDefault(
                    ModDataComponents.INTRINSIC_ENCHANTMENTS.get(), IntrinsicEnchants.EMPTY);
            stack.set(ModDataComponents.INTRINSIC_ENCHANTMENTS.get(), stored.with(key, effective + 1));
        }
    }

    /**
     * Effective level of one of the ring's built-in enchantments: the highest of the
     * item's base level, the level stored on this stack and any level added with an anvil.
     */
    private static int effectiveLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        if (!(stack.getItem() instanceof FlightRingItem ring)) {
            return 0;
        }
        int level = ring.getIntrinsicLevels(stack).getOrDefault(key, 0);
        // Levels added with an anvil live in the vanilla enchantments component.
        for (var entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().unwrapKey().filter(key::equals).isPresent()) {
                level = Math.max(level, entry.getValue());
            }
        }
        return level;
    }

    @Override
    public RecipeSerializer<? extends NormalCraftingRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(List.of(
                Optional.of(this.ring), Optional.of(this.catalyst), Optional.of(this.binder)));
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(
                new ShapelessCraftingRecipeDisplay(
                        List.of(this.ring.display(), this.catalyst.display(), this.binder.display()),
                        new SlotDisplay.ItemStackSlotDisplay(this.result),
                        new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)
                )
        );
    }
}
