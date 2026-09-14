package com.flightring;

import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

/**
 * Master librarians (level 5) sell the broken emerald flight ring: 32 emeralds, 3 uses,
 * restocked like every other librarian trade. Repairing it with an emerald block in the
 * crafting table yields the working emerald ring - that is the only way to obtain it.
 * <p>
 * The offer is deliberately NOT guaranteed: only about half of the master librarians roll
 * it (a returned {@code null} listing is skipped by vanilla, see
 * {@code AbstractVillager#addOffersFromItemListings}), so finding one takes a few librarians.
 * <p>
 * NeoForge 1.21.1 only. 26.1.2 moved villager trades into data packs, so that project
 * uses {@code data/simpleflightring/villager_trade/librarian/5/...} plus the
 * {@code minecraft:librarian/level_5} trade tag instead of this class.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class VillagerTradeHandler {

    /** Master librarian level that gets the offer. */
    private static final int MASTER_LEVEL = 5;
    /** Chance that a given master librarian offers the broken ring at all. */
    private static final float OFFER_CHANCE = 0.5F;
    private static final int EMERALD_PRICE = 32;
    private static final int MAX_USES = 3;
    private static final int XP = 30;
    private static final float PRICE_MULTIPLIER = 0.05F;

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.LIBRARIAN) {
            return;
        }
        var trades = event.getTrades().get(MASTER_LEVEL);
        if (trades == null) {
            return;
        }
        trades.add((trader, random) -> {
            // Rolled when the librarian turns master (updateTrades runs on career up, a
            // restock only resets the uses): vanilla skips a null offer, so roughly half
            // of all master librarians never sell it and the player has to keep looking.
            if (random.nextFloat() >= OFFER_CHANCE) {
                return null;
            }
            return new MerchantOffer(
                    new ItemCost(Items.EMERALD, EMERALD_PRICE),
                    new ItemStack(ModItems.DAMAGED_EMERALD_FLIGHT_RING.get()),
                    MAX_USES, XP, PRICE_MULTIPLIER);
        });
    }

    private VillagerTradeHandler() {
    }
}
