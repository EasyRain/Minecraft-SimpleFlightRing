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
public class RingRepairRecipe extends NormalCraftingRecipe {

    public static final MapCodec<RingRepairRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(ring -> ring.commonInfo),
                    CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(ring -> ring.bookInfo),
                    Ingredient.CODEC.listOf().optionalFieldOf("materials", List.of())
                            .forGetter(ring -> ring.materials),
                    Ingredient.CODEC.optionalFieldOf("material").forGetter(
                            ring -> ring.materials.size() == 1 ? Optional.of(ring.materials.get(0)) : Optional.empty()),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(ring -> ring.result)
            ).apply(instance, RingRepairRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RingRepairRecipe> STREAM_CODEC =
            StreamCodec.of(RingRepairRecipe::toNetwork, RingRepairRecipe::fromNetwork);

    private static RingRepairRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
        Recipe.CommonInfo common = Recipe.CommonInfo.STREAM_CODEC.decode(buffer);
        CraftingRecipe.CraftingBookInfo book = CraftingRecipe.CraftingBookInfo.STREAM_CODEC.decode(buffer);
        int count = buffer.readVarInt();
        List<Ingredient> materials = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            materials.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
        }
        ItemStackTemplate result = ItemStackTemplate.STREAM_CODEC.decode(buffer);
        return new RingRepairRecipe(common, book, materials, result);
    }

    private static void toNetwork(RegistryFriendlyByteBuf buffer, RingRepairRecipe recipe) {
        Recipe.CommonInfo.STREAM_CODEC.encode(buffer, recipe.commonInfo);
        CraftingRecipe.CraftingBookInfo.STREAM_CODEC.encode(buffer, recipe.bookInfo);
        buffer.writeVarInt(recipe.materials.size());
        for (Ingredient material : recipe.materials) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, material);
        }
        ItemStackTemplate.STREAM_CODEC.encode(buffer, recipe.result);
    }

    public static final RecipeSerializer<RingRepairRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final List<Ingredient> materials;
    private final ItemStackTemplate result;
    private final boolean fullRepair;

    public RingRepairRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
                            List<Ingredient> materials, ItemStackTemplate result) {
        this(commonInfo, bookInfo, materials, Optional.empty(), result);
    }

    /** Accepts both {@code materials: [...]} and the older single {@code material: ...}. */
    public RingRepairRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
                            List<Ingredient> materials, Optional<Ingredient> single, ItemStackTemplate result) {
        super(commonInfo, bookInfo);
        List<Ingredient> combined = new ArrayList<>(materials);
        if (combined.isEmpty()) {
            single.ifPresent(combined::add);
        }
        this.materials = List.copyOf(combined);
        this.result = result;
        this.fullRepair = result.item().is(ModItems.NETHERITE_FLIGHT_RING);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack ring = ItemStack.EMPTY;
        boolean[] matched = new boolean[this.materials.size()];
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(this.result.item())) {
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

    public List<Ingredient> getMaterials() {
        return this.materials;
    }

    @Override
    public RecipeSerializer<? extends NormalCraftingRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        List<Optional<Ingredient>> slots = new ArrayList<>();
        for (Ingredient material : this.materials) {
            slots.add(Optional.of(material));
        }
        slots.add(Optional.of(Ingredient.of(this.result.item().value())));
        return PlacementInfo.createFromOptionals(slots);
    }

    @Override
    public List<RecipeDisplay> display() {
        List<SlotDisplay> shown = new ArrayList<>();
        for (Ingredient material : this.materials) {
            shown.add(material.display());
        }
        shown.add(new SlotDisplay.ItemSlotDisplay(this.result.item()));
        return List.of(
                new ShapelessCraftingRecipeDisplay(
                        shown,
                        new SlotDisplay.ItemStackSlotDisplay(this.result),
                        new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)
                )
        );
    }
}
