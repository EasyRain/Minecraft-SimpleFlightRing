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

    /** Whether the ring carries the enchantment below the cap. */
    private static boolean canRaise(ItemStack stack, ResourceKey<Enchantment> key) {
        Holder<Enchantment> holder = findHolder(stack, key);
        return holder != null && effectiveLevel(stack, holder) < MAX_LEVEL;
    }

    /**
     * Raises the ring's INTRINSIC (built-in) level by one, never above the cap. The
     * intrinsic level is the permanent part; a higher level added with an anvil is
     * respected and simply carried over, so only enchantments below the cap are raised.
     */
    private static void raise(ItemStack stack, ResourceKey<Enchantment> key) {
        Holder<Enchantment> holder = findHolder(stack, key);
        if (holder == null) {
            return;
        }
        int effective = effectiveLevel(stack, holder);
        if (effective < MAX_LEVEL) {
            SpecialRings.setIntrinsic(stack, holder, effective + 1);
        }
    }

    /** The higher of the intrinsic (built-in) level and the real enchantment level. */
    private static int effectiveLevel(ItemStack stack, Holder<Enchantment> holder) {
        int intrinsic = stack.getItem() instanceof FlightRingItem ring
                ? ring.getIntrinsicEnchantLevel(stack, holder)
                : 0;
        return Math.max(intrinsic, stack.getEnchantments().getLevel(holder));
    }

    /** The existing holder for the key, from the ring's intrinsic or real enchantments. */
    private static Holder<Enchantment> findHolder(ItemStack stack, ResourceKey<Enchantment> key) {
        ItemEnchantments intrinsic = stack.get(ModDataComponents.INTRINSIC_ENCHANTMENTS.get());
        if (intrinsic != null) {
            for (var entry : intrinsic.entrySet()) {
                if (matches(entry.getKey(), key)) {
                    return entry.getKey();
                }
            }
        }
        for (var entry : stack.getEnchantments().entrySet()) {
            if (matches(entry.getKey(), key)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static boolean matches(Holder<Enchantment> holder, ResourceKey<Enchantment> key) {
        return holder.unwrapKey().filter(key::equals).isPresent();
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
