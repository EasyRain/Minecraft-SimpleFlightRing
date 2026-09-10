package com.flightring;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/**
 * Shapeless upgrade for the Powered Flight Ring: ring + gunpowder + redstone dust.
 * Raises each of the ring's BUILT-IN enchantments (Rocket Boost and Efficiency) by one
 * level, capped at 3 - an enchantment already at (or above) the cap is left untouched,
 * so levels added with an anvil are respected. No other enchantment is ever modified.
 * The recipe does not match when nothing can be raised (both already at the cap).
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
        if (found.isEmpty() || catalysts != 1 || binders != 1) {
            return false;
        }
        HolderLookup.Provider registries = level.registryAccess();
        return canRaise(found, registries, ModEnchantments.ROCKET_BOOST)
                || canRaise(found, registries, Enchantments.EFFICIENCY);
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
        raise(upgraded, registries, ModEnchantments.ROCKET_BOOST);
        raise(upgraded, registries, Enchantments.EFFICIENCY);
        return upgraded;
    }

    private static boolean canRaise(ItemStack stack, HolderLookup.Provider registries, ResourceKey<Enchantment> key) {
        return effectiveLevel(stack, registries.holderOrThrow(key)) < MAX_LEVEL;
    }

    private static void raise(ItemStack stack, HolderLookup.Provider registries, ResourceKey<Enchantment> key) {
        Holder<Enchantment> enchantment = registries.holderOrThrow(key);
        int effective = effectiveLevel(stack, enchantment);
        if (effective < MAX_LEVEL) {
            // The upgrade raises the ring's INTRINSIC (built-in) level - it is the
            // permanent part. A higher level added with an anvil is respected and
            // simply carried over, so only enchantments below the cap are raised.
            SpecialRings.setIntrinsic(stack, enchantment, effective + 1);
        }
    }

    /** The higher of the intrinsic (built-in) level and the real enchantment level. */
    private static int effectiveLevel(ItemStack stack, Holder<Enchantment> enchantment) {
        int intrinsic = stack.getItem() instanceof FlightRingItem ring
                ? ring.getIntrinsicEnchantLevel(stack, enchantment)
                : 0;
        return Math.max(intrinsic, stack.getEnchantmentLevel(enchantment));
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
