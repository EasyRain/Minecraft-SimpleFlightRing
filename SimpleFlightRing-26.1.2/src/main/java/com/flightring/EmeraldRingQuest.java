package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The emerald relic ring's quest and its strength.
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
 * one poll per second is more than enough to catch it. One won raid pays out exactly ONE
 * ring: the raid that already did is remembered per player (by its centre) for as long as
 * that victory lasts, so a player carrying several sleeping rings has to win one raid per
 * ring instead of having the whole inventory charged within the 30 second celebration.
 * <p>
 * The raid's ominous level becomes the ring's strength ({@link HeroLevel}): a ring woken in a
 * level III raid grants Hero of the Village III. A ring that is already awake - damaged but
 * charged, or already forged - is strengthened instead when the raid was stronger than it is,
 * and the level follows the ring into the working one through {@link EmeraldForgeRecipe}.
 * A sleeping ring is always woken first: quest before upgrades.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class EmeraldRingQuest {

    /** Poll interval: the raid celebrates for 30 s after the victory, once a second is plenty. */
    private static final int CHECK_INTERVAL_TICKS = 20;
    /** Feedback when the ring wakes up. */
    private static final String HERO_AWAKENED = "message.simpleflightring.emerald_hero_awakened";
    /** Feedback when an already awake ring grows to the level of the raid that was just won. */
    private static final String HERO_STRENGTHENED = "message.simpleflightring.emerald_hero_strengthened";

    /**
     * Which won raid already woke a ring for a player, by the raid's centre. The celebration
     * lasts 30 s and this code polls once a second, so without this the whole inventory would
     * be charged one ring per second - and the tick after the celebration ends the entry is
     * dropped again, so the next raid counts as a new one.
     */
    private static final Map<UUID, BlockPos> RAID_WOKE = new HashMap<>();

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
            // No victory in progress (anymore): forget the last one, so the next raid counts.
            RAID_WOKE.remove(player.getUUID());
            return;
        }
        if (raid.getCenter().equals(RAID_WOKE.get(player.getUUID()))) {
            return;                          // this very victory already did its one ring
        }
        // The raid's ominous level (1..5) is what the ring takes away from the victory.
        int level = HeroLevel.clamp(raid.getRaidOmenLevel());
        ItemStack ring = findSleepingRing(player);
        boolean awaken = !ring.isEmpty();
        if (!awaken) {
            // Nothing left to wake: a ring that is already awake (damaged or forged) still
            // grows when the raid was stronger than the light it carries.
            ring = findWeakerRing(player, level);
            if (ring.isEmpty()) {
                return;
            }
        }

        if (awaken) {
            ring.set(ModDataComponents.HERO_CHARGED.get(), Unit.INSTANCE);
        }
        HeroLevel.set(ring, level);
        RAID_WOKE.put(player.getUUID(), raid.getCenter());

        ServerLevel serverLevel = player.level();
        // The village hero's own particles and the advancement jingle: this is a celebration.
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0, player.getZ(),
                24, 0.5, 0.6, 0.5, 0.05);
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 0.8F, 1.0F);
        player.sendOverlayMessage(Component.translatable(
                awaken ? HERO_AWAKENED : HERO_STRENGTHENED, HeroLevel.roman(level))
                .withStyle(ChatFormatting.GRAY));

        FlightRingMod.LOGGER.debug("[FlightRing] {} {} the emerald ring (level {}) with a raid victory",
                player.getName().getString(), awaken ? "woke" : "strengthened", level);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        RAID_WOKE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        RAID_WOKE.remove(event.getOriginal().getUUID());
    }

    /**
     * The carried broken emerald ring that is still asleep, or an empty stack. Only this one
     * stack is charged: one won raid = one ring, even when the player carries several of them.
     */
    private static ItemStack findSleepingRing(ServerPlayer player) {
        return findRing(player, stack -> stack.getItem() instanceof DamagedRingItem damaged
                && damaged.relic() == RelicRing.EMERALD
                && !DamagedRingItem.isHeroCharged(stack));
    }

    /**
     * The carried emerald ring - damaged but already awake, or forged and worn - whose light
     * is still weaker than the raid that was just won, or an empty stack.
     * <p>
     * A ring that already sits at the raid's level is skipped and the victory is passed on to
     * the next candidate, so a level V ring - the strongest a raid can ever give - never
     * absorbs a victory: the raid strengthens another ring instead, and when there is none the
     * victory simply does nothing (and stays unused for a ring the player picks up while the
     * raid is still celebrating). Quest first: a sleeping ring is woken before any ring is
     * strengthened (see the caller).
     */
    private static ItemStack findWeakerRing(ServerPlayer player, int level) {
        return findRing(player, stack -> isEmeraldRing(stack) && HeroLevel.of(stack) < level);
    }

    /** True for both forms of the emerald ring: the awakened broken one and the working one. */
    private static boolean isEmeraldRing(ItemStack stack) {
        if (stack.getItem() instanceof DamagedRingItem damaged) {
            return damaged.relic() == RelicRing.EMERALD && DamagedRingItem.isHeroCharged(stack);
        }
        return stack.getItem() instanceof FlightRingItem ring && ring.hasAbility(RingAbility.EMERALD_HERO);
    }

    /**
     * The first carried stack matching {@code match}, looking at the Curios slot, the
     * inventory and the offhand - the same order {@link SculkRingQuest} uses.
     */
    private static ItemStack findRing(ServerPlayer player, Predicate<ItemStack> match) {
        ItemStack worn = CuriosCompat.isLoaded() ? CuriosCompat.findRingInSlot(player) : ItemStack.EMPTY;
        if (match.test(worn)) {
            return worn;
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (match.test(stack)) {
                return stack;
            }
        }
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        return match.test(offhand) ? offhand : ItemStack.EMPTY;
    }

    private EmeraldRingQuest() {
    }
}
