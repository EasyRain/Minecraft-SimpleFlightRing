package com.flightring;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
                 * Attribute bonuses granted while the ring is worn, applied by Curios to the
                 * wearer every tick and listed in the tooltip; a ring carried in the inventory
                 * is never consulted here, so it only gives flight time.
                 * <ul>
                 *   <li><b>linked rings</b> (the AllTheModium chain): armour, toughness, attack
                 *       damage and reach, see {@link RingBonuses};</li>
                 *   <li><b>relic rings</b>: their own mix of armour, toughness, damage, attack
                 *       speed, movement speed, max health, reach, luck and dodge, see
                 *       {@link RelicBonuses}. The dodge goes to the mythic
                 *       {@code apothic_attributes:dodge_chance} when that mod is installed and to
                 *       our own {@link ModAttributes#DODGE_CHANCE} otherwise (see
                 *       {@link ApothicAttributesCompat#dodgeTarget()}) - either way Curios lists
                 *       it on the tooltip and exactly one handler rolls it;</li>
                 *   <li><b>ocean</b>: {@code SUBMERGED_MINING_SPEED} - vanilla multiplies the
                 *       mining speed by 0.2 whenever the eyes are in water, so +0.8 brings it
                 *       back to 1.0, i.e. no slowdown underwater.</li>
                 * </ul>
                 */
                @Override
                public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext,
                                                                                            ResourceLocation id,
                                                                                            ItemStack stack) {
                    Multimap<Holder<Attribute>, AttributeModifier> modifiers = LinkedHashMultimap.create();
                    if (!(stack.getItem() instanceof FlightRingItem item) || !item.isUsable(stack)) {
                        return modifiers;
                    }
                    RingBonuses bonuses = item.getBonuses();
                    if (bonuses != null) {
                        modifiers.putAll(bonuses.attributeModifiers());
                    }
                    RelicBonuses relicBonuses = item.getRelicBonuses();
                    if (relicBonuses != null) {
                        modifiers.putAll(relicBonuses.attributeModifiers(ApothicAttributesCompat.dodgeTarget()));
                    }
                    if (item.hasAbility(RingAbility.OCEAN_FAVORED)) {
                        modifiers.put(Attributes.SUBMERGED_MINING_SPEED, new AttributeModifier(
                                ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "ocean_submerged_mining"),
                                OceanFavoredAbility.submergedMiningBonus(), AttributeModifier.Operation.ADD_VALUE));
                    }
                    return modifiers;
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
