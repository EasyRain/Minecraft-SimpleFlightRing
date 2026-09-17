package com.flightring;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The damaged raid ring's trial: the illager will watches while the wearer proves itself by
 * felling three iron golems inside one minute, and only then acknowledges the ring
 * ({@link ModDataComponents#ILLAGER_ACKNOWLEDGED}). The clock starts with the first blow the
 * wearer lands on a golem, so the trial cannot be prepared in advance, and a failed attempt is
 * followed by a cooldown before the will looks again.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class RaidRingQuest {

    /** Sixty seconds, counted from the first blow. */
    private static final int TRIAL_TICKS = 20 * 60;
    /** Either outcome is followed by half a minute of silence. */
    private static final int COOLDOWN_TICKS = 20 * 30;
    /** How many golems the will asks for. */
    private static final int GOLEMS_REQUIRED = 3;

    private static final Map<UUID, Long> TRIAL_ENDS = new HashMap<>();
    private static final Map<UUID, Integer> KILLS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_ENDS = new HashMap<>();

    /** The first blow on a golem starts the trial. */
    @SubscribeEvent
    public static void onGolemHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof IronGolem)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        if (!carriesUnacknowledged(player) || TRIAL_ENDS.containsKey(id) || isCoolingDown(player)) {
            return;
        }
        TRIAL_ENDS.put(id, player.level().getGameTime() + TRIAL_TICKS);
        KILLS.put(id, 0);
        showOverlay(player, "message.simpleflightring.raid_quest_watching");
    }

    /** The third golem felled inside the window acknowledges the ring. */
    @SubscribeEvent
    public static void onGolemDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof IronGolem)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        if (!TRIAL_ENDS.containsKey(id)) {
            return;
        }
        int kills = KILLS.merge(id, 1, Integer::sum);
        if (kills < GOLEMS_REQUIRED) {
            return;
        }
        ItemStack ring = findUnacknowledged(player);
        if (!ring.isEmpty()) {
            ring.set(ModDataComponents.ILLAGER_ACKNOWLEDGED.get(), net.minecraft.util.Unit.INSTANCE);
        }
        showOverlay(player, "message.simpleflightring.raid_quest_passed");
        endTrial(player);
    }

    /** Running out of time fails the trial, and either way the will falls silent for a while. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Long end = TRIAL_ENDS.get(player.getUUID());
        if (end != null && player.level().getGameTime() > end) {
            showOverlay(player, "message.simpleflightring.raid_quest_failed");
            endTrial(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        forget(event.getEntity().getUUID());
    }

    private static void endTrial(ServerPlayer player) {
        UUID id = player.getUUID();
        TRIAL_ENDS.remove(id);
        KILLS.remove(id);
        COOLDOWN_ENDS.put(id, player.level().getGameTime() + COOLDOWN_TICKS);
    }

    private static void forget(UUID id) {
        TRIAL_ENDS.remove(id);
        KILLS.remove(id);
        COOLDOWN_ENDS.remove(id);
    }

    private static boolean isCoolingDown(Player player) {
        Long until = COOLDOWN_ENDS.get(player.getUUID());
        return until != null && player.level().getGameTime() <= until;
    }

    private static boolean carriesUnacknowledged(Player player) {
        return !findUnacknowledged(player).isEmpty();
    }

    /**
     * The first damaged raid ring the player carries that the will has not acknowledged yet -
     * in the inventory, the off hand or the Curios slot, exactly like the other relics' quests.
     */
    private static ItemStack findUnacknowledged(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isDamagedRaidRing(stack)) {
                return stack;
            }
        }
        if (isDamagedRaidRing(player.getOffhandItem())) {
            return player.getOffhandItem();
        }
        if (CuriosCompat.isLoaded()) {
            ItemStack curio = CuriosCompat.findRingInSlot(player);
            if (isDamagedRaidRing(curio)) {
                return curio;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isDamagedRaidRing(ItemStack stack) {
        return stack.getItem() instanceof DamagedRingItem damaged
                && damaged.relic() == RelicRing.RAID
                && !stack.has(ModDataComponents.ILLAGER_ACKNOWLEDGED.get());
    }

    private static void showOverlay(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withColor(RingAbility.RAID_PLUNDER.color()), true);
    }

    private RaidRingQuest() {
    }
}
