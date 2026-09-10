package com.flightring;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/**
 * Shapeless upgrade for the Powered Flight Ring: ring + gunpowder + redstone dust.
 * Raises each of the ring's BUILT-IN (intrinsic) enchantments - Rocket Boost and
 * Efficiency - by one level, capped at 3. The effective level is the highest of the
 * ring's base level, the level already stored on it and any level added with an anvil,
 * so an enchantment that is already at the cap is left untouched. No other
 * enchantment is ever modified, and the recipe does not match when nothing can be raised.
 */
public class RingEnchantUpgradeRecipe extends ShapelessRecipe {

    /** Highest level the built-in enchantments can be raised to. */
    public static final int MAX_LEVEL = 3;

    private final Ingredient ring;
    private final Ingredient catalyst;
    private final Ingredient binder;
    private final ItemStack result;

    public RingEnchantUpgradeRecipe(String group, CraftingBookCategory category, Ingredient ring,
                                    Ingredient catalyst, Ingredient binder, ItemStack result) {
        super(group, category, result, NonNullList.of(Ingredient.EMPTY, ring, catalyst, binder));
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
        return !found.isEmpty() && catalysts == 1 && binders == 1
                && (canRaise(found, ModEnchantments.ROCKET_BOOST) || canRaise(found, Enchantments.EFFICIENCY));
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
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

    public Ingredient getRing() {
        return this.ring;
    }

    public Ingredient getCatalyst() {
        return this.catalyst;
    }

    public Ingredient getBinder() {
        return this.binder;
    }

    public ItemStack getResult() {
        return this.result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.RING_ENCHANT_UPGRADE.get();
    }

    public static class Serializer implements RecipeSerializer<RingEnchantUpgradeRecipe> {

        public static final MapCodec<RingEnchantUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(RingEnchantUpgradeRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(RingEnchantUpgradeRecipe::category),
                Ingredient.CODEC.fieldOf("ring").forGetter(RingEnchantUpgradeRecipe::getRing),
                Ingredient.CODEC.fieldOf("catalyst").forGetter(RingEnchantUpgradeRecipe::getCatalyst),
                Ingredient.CODEC.fieldOf("binder").forGetter(RingEnchantUpgradeRecipe::getBinder),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(RingEnchantUpgradeRecipe::getResult)
        ).apply(instance, RingEnchantUpgradeRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, RingEnchantUpgradeRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<RingEnchantUpgradeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, RingEnchantUpgradeRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static RingEnchantUpgradeRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
            Ingredient ring = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            Ingredient catalyst = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            Ingredient binder = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            return new RingEnchantUpgradeRecipe(group, category, ring, catalyst, binder, result);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, RingEnchantUpgradeRecipe recipe) {
            buffer.writeUtf(recipe.getGroup());
            buffer.writeEnum(recipe.category());
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.ring);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.catalyst);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.binder);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
        }
    }
}
