package com.flightring;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shapeless repair recipe: one flight ring + the listed materials = one craft.
 * <p>
 * Each craft restores 25% durability (netherite restores 100%); repeat the craft to fully repair a ring. The
 * materials are <b>shapeless</b>, so position never matters and a recipe with one or two materials also fits in
 * the 2x2 grid. Each entry in {@code materials} takes its own slot, which is how "two ingots per craft" is
 * expressed - a stack of two in a single slot does <i>not</i> count twice, exactly like vanilla per-slot
 * consumption. Container items (a lava bucket) come back through the vanilla crafting-remainder handling.
 * <p>
 * Enchantments, affixes, custom name and lore are preserved by copying the input ring. A fully-durable ring
 * cannot be used.
 */
public class RingRepairRecipe extends ShapelessRecipe {

    private final List<Ingredient> materials;
    private final ItemStack result;
    private final boolean fullRepair;

    public RingRepairRecipe(String group, CraftingBookCategory category, List<Ingredient> materials, ItemStack result) {
        super(group, category, result, ingredients(materials, result));
        this.materials = List.copyOf(materials);
        this.result = result;
        this.fullRepair = result.is(ModItems.NETHERITE_FLIGHT_RING.get());
    }

    /** The whole grid this recipe occupies: every material slot plus the ring itself. */
    private static NonNullList<Ingredient> ingredients(List<Ingredient> materials, ItemStack result) {
        NonNullList<Ingredient> all = NonNullList.create();
        all.addAll(materials);
        all.add(Ingredient.of(result.getItem()));
        return all;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack ring = ItemStack.EMPTY;
        boolean[] matched = new boolean[this.materials.size()];
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(this.result.getItem())) {
                if (!ring.isEmpty()) {
                    return false;
                }
                ring = stack;
                continue;
            }
            int slot = -1;
            for (int i = 0; i < this.materials.size(); i++) {
                if (!matched[i] && this.materials.get(i).test(stack)) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) {
                return false;
            }
            matched[slot] = true;
        }
        if (ring.isEmpty() || ring.getDamageValue() <= 0) {
            return false;
        }
        for (boolean used : matched) {
            if (!used) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack ring = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.is(this.result.getItem())) {
                ring = stack;
                break;
            }
        }
        ItemStack repaired = ring.copy();
        int repair = this.fullRepair ? ring.getMaxDamage() : ring.getMaxDamage() / 4;
        repaired.setDamageValue(Math.max(0, ring.getDamageValue() - repair));
        return repaired;
    }

    public List<Ingredient> getMaterials() {
        return this.materials;
    }

    public ItemStack getResult() {
        return this.result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.RING_REPAIR.get();
    }

    public static class Serializer implements RecipeSerializer<RingRepairRecipe> {

        public static final MapCodec<RingRepairRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                com.mojang.serialization.Codec.STRING.optionalFieldOf("group", "").forGetter(RingRepairRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(RingRepairRecipe::category),
                Ingredient.CODEC.listOf().optionalFieldOf("materials", List.of()).forGetter(RingRepairRecipe::getMaterials),
                Ingredient.CODEC.optionalFieldOf("material").forGetter(
                        ring -> ring.getMaterials().size() == 1 ? Optional.of(ring.getMaterials().get(0)) : Optional.empty()),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(RingRepairRecipe::getResult)
        ).apply(instance, RingRepairRecipe.Serializer::create));

        /** Accepts both {@code materials: [...]} and the older single {@code material: {...}}. */
        private static RingRepairRecipe create(String group, CraftingBookCategory category, List<Ingredient> list,
                                               Optional<Ingredient> single, ItemStack result) {
            List<Ingredient> materials = new ArrayList<>(list);
            if (materials.isEmpty()) {
                single.ifPresent(materials::add);
            }
            return new RingRepairRecipe(group, category, materials, result);
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, RingRepairRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<RingRepairRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, RingRepairRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static RingRepairRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
            int count = buffer.readVarInt();
            List<Ingredient> materials = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                materials.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
            }
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            return new RingRepairRecipe(group, category, materials, result);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, RingRepairRecipe recipe) {
            buffer.writeUtf(recipe.getGroup());
            buffer.writeEnum(recipe.category());
            buffer.writeVarInt(recipe.materials.size());
            for (Ingredient material : recipe.materials) {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, material);
            }
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
        }
    }
}
