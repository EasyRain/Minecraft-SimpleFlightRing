package com.flightring;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Optional Curios API integration.
 * <p>
 * This class must only be touched when Curios is actually loaded (see {@link #isLoaded()}),
 * so that the mod keeps working without the dependency.
 */
public final class CuriosCompat {

    /** Identifier of the extra "flight ring" slot type. */
    public static final String SLOT_ID = "flight_ring";

    private static boolean registered = false;

    private CuriosCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("curios");
    }

    /** Registers the curio behavior (right-click equipping) for every ring. */
    public static void register() {
        if (registered) {
            return;
        }
        for (var ring : ModItems.ALL) {
            CuriosApi.registerCurio(ring.get(), new ICurioItem() {

                /**
                 * The linked (AllTheModium chain) rings grant armour, armour toughness,
                 * attack damage and reach while worn. Curios applies these to the wearer
                 * every tick and also lists them in the tooltip; a ring carried in the
                 * inventory is never consulted here, so it only gives flight time.
                 */
                @Override
                public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext,
                                                                                            ResourceLocation id,
                                                                                            ItemStack stack) {
                    RingBonuses bonuses = ring.get().getBonuses();
                    return bonuses == null ? ImmutableMultimap.of() : bonuses.attributeModifiers();
                }
            });
        }
        registered = true;
    }

    /** Returns the ring equipped in the flight ring slot, or {@link ItemStack#EMPTY}. */
    public static ItemStack findRingInSlot(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findCurio(SLOT_ID, 0))
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }
}
