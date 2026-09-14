package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The first step of the emerald relic ring's quest.
 * <p>
 * The broken emerald ring is only sold by master librarians. While it is still asleep (no
 * {@code hero_charged} component) winning a raid with it CARRYING it - anywhere in the
 * inventory, the offhand or the Curios slot - wakes it up: the stack gains the component,
 * starts to glint and its tooltip swaps the last line ("guard a village one more time") for
 * the state line plus the missing vessel, which is the recipe gate for forging the working
 * ring out of 8 emeralds (see {@code emerald_flight_ring.json}).
 * <p>
 * The raid is detected the vanilla way: {@code ServerLevel#getRaidAt} returns the active
 * raid within 96 blocks of the player (exactly the range vanilla uses for the raid itself),
 * and a raid whose status is VICTORY keeps celebrating for 30 seconds before it stops - so
 * one poll per second is more than enough to catch it. One won raid wakes exactly ONE ring:
 * if the player carries several sleeping rings, only the first one found is charged.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class EmeraldRingQuest {

    /** Poll interval: the raid celebrates for 30 s after the victory, once a second is plenty. */
    private static final int CHECK_INTERVAL_TICKS = 20;
    /** Feedback when the ring wakes up. */
    private static final String HERO_AWAKENED = "message.simpleflightring.emerald_hero_awakened";

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().getGameTime() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        // Cheap test first: there usually is no raid at all, and the raid map is empty then.
        Raid raid = player.level().getRaidAt(player.blockPosition());
        if (raid == null || !raid.isVictory()) {
            return;
        }
        ItemStack ring = findSleepingRing(player);
        if (ring.isEmpty()) {
            return;
        }

        ring.set(ModDataComponents.HERO_CHARGED.get(), Unit.INSTANCE);

        ServerLevel level = player.level();
        // The village hero's own particles and the advancement jingle: this is a celebration.
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0, player.getZ(),
                24, 0.5, 0.6, 0.5, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 0.8F, 1.0F);
        player.sendOverlayMessage(
                Component.translatable(HERO_AWAKENED).withStyle(ChatFormatting.GRAY));

        FlightRingMod.LOGGER.debug("[FlightRing] {} woke the emerald ring up with a raid victory",
                player.getName().getString());
    }

    /**
     * The carried broken emerald ring that is still asleep, or an empty stack. Only this one
     * stack is charged: one won raid = one ring, even when the player carries several of them.
     */
    private static ItemStack findSleepingRing(ServerPlayer player) {
        ItemStack worn = CuriosCompat.isLoaded() ? CuriosCompat.findRingInSlot(player) : ItemStack.EMPTY;
        if (isSleeping(worn)) {
            return worn;
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (isSleeping(stack)) {
                return stack;
            }
        }
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        return isSleeping(offhand) ? offhand : ItemStack.EMPTY;
    }

    private static boolean isSleeping(ItemStack stack) {
        return stack.getItem() instanceof DamagedRingItem damaged
                && damaged.relic() == RelicRing.EMERALD
                && !DamagedRingItem.isHeroCharged(stack);
    }

    private EmeraldRingQuest() {
    }
}
