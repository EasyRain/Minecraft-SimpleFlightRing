package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The two steps of the ender relic ring's quest.
 * <p>
 * The broken ender ring is found in end city treasure. Its path is the dragon's own: killing the
 * ENDER DRAGON while carrying the broken ring (anywhere in the inventory, the offhand or the Curios
 * slot) makes the ring swallow the death of the thing that rules the End, so it gains
 * {@link ModDataComponents#DRAGON_CHARGED}, starts to glint and its tooltip asks for the vessel.
 * One dragon charges exactly ONE ring.
 * <p>
 * The vessel itself is crafted: the charged broken ring in the middle of a table with ender pearls
 * above, below and to both sides and shulker shells in the corners - see the
 * {@code ender_flight_ring} recipe, which is gated on the {@code dragon_charged} component so an
 * uncharged ring simply does not fit.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class EnderRingQuest {

    /** Feedback when the ring swallows the dragon's death. */
    private static final String DRAGON_CHARGED_MESSAGE = "message.simpleflightring.ender_dragon_charged";

    /** The dragon dies, the ring it was carried against takes its place as the heir of the End. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().getType() != EntityType.ENDER_DRAGON) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack ring = findWaitingRing(player);
        if (ring.isEmpty()) {
            return;
        }

        ring.set(ModDataComponents.DRAGON_CHARGED.get(), Unit.INSTANCE);

        ServerLevel level = player.serverLevel();
        // The End's own particles, the ones its portals are made of.
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + 0.8, player.getZ(),
                48, 0.6, 0.9, 0.6, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.9F, 1.2F);
        player.displayClientMessage(
                Component.translatable(DRAGON_CHARGED_MESSAGE).withStyle(ChatFormatting.GRAY), true);

        FlightRingMod.LOGGER.debug("[FlightRing] {} fed the ender dragon's death to the ender ring",
                player.getName().getString());
    }

    /**
     * The carried broken ender ring that is still waiting, or an empty stack. Only this one stack
     * is charged: one dragon = one ring, even when the player carries several.
     */
    private static ItemStack findWaitingRing(ServerPlayer player) {
        ItemStack worn = CuriosCompat.isLoaded() ? CuriosCompat.findRingInSlot(player) : ItemStack.EMPTY;
        if (isWaiting(worn)) {
            return worn;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (isWaiting(stack)) {
                return stack;
            }
        }
        ItemStack offhand = player.getInventory().offhand.get(0);
        return isWaiting(offhand) ? offhand : ItemStack.EMPTY;
    }

    private static boolean isWaiting(ItemStack stack) {
        return stack.getItem() instanceof DamagedRingItem damaged
                && damaged.relic() == RelicRing.ENDER
                && !DamagedRingItem.isDragonCharged(stack);
    }

    private EnderRingQuest() {
    }
}
