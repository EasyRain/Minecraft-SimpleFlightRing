package com.flightring;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CurioAttributeModifiers;
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
                @Override
                public void onEquipFromUse(SlotContext slotContext, ItemStack stack) {
                    // Curios 15.x plays the equip sound whenever the slot content is
                    // synchronized (e.g. while the Curios menu is open). The flight ring's
                    // durability changes every second while flying, which would trigger the
                    // sound once per second. Suppress it here.
                }

                @Override
                public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
                    // Only play the equip sound when a different item is actually put into
                    // the slot; pure data changes (durability drain) stay silent.
                    if (!ItemStack.isSameItem(prevStack, stack)) {
                        ICurioItem.super.onEquip(slotContext, prevStack, stack);
                    }
                }

                /**
                 * The linked (AllTheModium chain) rings grant armour, armour toughness,
                 * attack damage and reach while worn.
                 * <p>
                 * Curios 15 collects curio attributes through the STATIC
                 * {@code ICurioItem#forEachModifier(stack, slotContext, consumer)}, which only
                 * looks at the {@code curios:attribute_modifiers} component and - when that
                 * component is absent - at this method. Overriding the legacy instance
                 * {@code getAttributeModifiers(SlotContext, Identifier, ItemStack)} does
                 * nothing here: both the per-tick attribute application and the tooltip use
                 * the static path, so the bonuses must come from this method.
                 */
                @Override
                public CurioAttributeModifiers getDefaultCurioAttributeModifiers(ItemStack stack) {
                    RingBonuses bonuses = ring.get().getBonuses();
                    if (bonuses == null) {
                        return CurioAttributeModifiers.EMPTY;
                    }
                    CurioAttributeModifiers.Builder builder = CurioAttributeModifiers.builder();
                    bonuses.attributeModifiers()
                            .forEach((attribute, modifier) -> builder.addModifier(attribute, modifier, SLOT_ID));
                    return builder.build().withTooltip(true);
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
