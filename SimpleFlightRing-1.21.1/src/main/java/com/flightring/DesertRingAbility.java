package com.flightring;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * "Desert Guide" - the ability of the desert relic ring, the ring of getting the most out of
 * very little. Passive, and like every other relic ability it only works while the ring is worn
 * in the Curios slot with durability left.
 * <ol>
 *   <li><b>A point of Luck</b>: a real attribute bonus ({@code Attributes.LUCK}), handed to
 *       Curios like the linked rings' armour, so the Luck <b>effect</b> still stacks on top of it
 *       and vanilla's luck driven loot rolls (fishing, chest loot) use the total.</li>
 *   <li><b>Hunger and Poison cannot touch the wearer</b>: refused in
 *       {@link MobEffectEvent.Applicable}, whatever the source.</li>
 *   <li><b>Half again as much out of every meal</b>: see the food mixin of this mod
 *       ({@code DesertFoodMixin}), which applies the extra nutrition on top of what vanilla
 *       gives.</li>
 *   <li><b>Danger sense</b>: pure client side, see {@link DesertSenseHandler} - it outlines
 *       living creatures within 32 blocks for the wearer alone.</li>
 * </ol>
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class DesertRingAbility {

    /** Attribute bonus the desert ring grants; a Luck potion adds its own levels on top. */
    static final double LUCK_BONUS = 1.0;

    /**
     * Extra food value the wearer gets, as a fraction of what the food normally gives: the food
     * mixin adds {@code nutrition * FOOD_BONUS} more food points (and the matching saturation),
     * so a 4 point meal counts as 6.
     */
    public static final float FOOD_BONUS = 0.5F;

    /**
     * Hunger and Poison are simply refused. Unlike the ocean ring's elder guardian curse this is
     * not scoped to a source: the desert ring makes the wearer immune to both, whoever tries.
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player) || wornRing(player).isEmpty()) {
            return;
        }
        MobEffect effect = event.getEffectInstance().getEffect().value();
        if (effect == MobEffects.HUNGER || effect == MobEffects.POISON) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /**
     * True while this player wears a usable desert ring, {@code false} for a null player.
     * Used by the food mixin, which only has the eating player to go by.
     */
    public static boolean isDesertWalker(Player player) {
        return player != null && !wornRing(player).isEmpty();
    }

    /** The worn desert ring, or an empty stack: needs the ability and durability left. */
    private static ItemStack wornRing(Player player) {
        if (!CuriosCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        if (ring.getItem() instanceof FlightRingItem item
                && item.hasAbility(RingAbility.DESERT_GUIDE)
                && item.isUsable(ring)) {
            return ring;
        }
        return ItemStack.EMPTY;
    }

    private DesertRingAbility() {
    }
}
