package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The infernal relic ring's quest, both steps of it.
 * <p>
 * The broken infernal ring is found in nether fortress chests. Its fire has gone out, and the two
 * things it needs are the two ends of the Nether's own weather:
 * <ol>
 *   <li><b>The Wither's death flame.</b> Killing a WITHER while carrying the broken ring - anywhere
 *       in the inventory, the offhand or the Curios slot - makes the ring swallow the flame: the
 *       stack gains {@link ModDataComponents#WITHER_CHARGED}, starts to glint and its tooltip asks
 *       for the quench. One Wither charges exactly ONE ring.</li>
 *   <li><b>The quench.</b> Throwing the charged ring into lava forges it into the working ring
 *       right there in the lava ({@link #tickInLava}), which then floats back up to the surface
 *       and waits for its owner to pick it up.</li>
 * </ol>
 * The steps are in that order on purpose: lava on its own does nothing but float the ring, so a
 * broken ring that never swallowed a death flame comes back out of the lava exactly as it went in.
 * <p>
 * Both the broken and the working ring are fire proof like netherite (see
 * {@code ModItems#infernalProof}), so neither is ever destroyed by the lava they are supposed to
 * be thrown into.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class InfernalRingQuest {

    /** Feedback when the ring swallows the Wither's death flame. */
    private static final String WITHER_CHARGED_MESSAGE = "message.simpleflightring.infernal_wither_charged";
    /** Feedback when the lava turns the broken ring into the working one. */
    private static final String QUENCHED_MESSAGE = "message.simpleflightring.infernal_quenched";

    /**
     * The lava's own idea of "the fluid is holding this item up": the same 0.1 vanilla uses to
     * decide whether a fallen item still gets pushed up. Deeper than that and the ring is under
     * the surface, at or above it the ring rides the surface.
     */
    private static final double SURFACE_LEVEL = 0.1;
    /** How far under the fluid's top a parked ring sits. */
    private static final double FLOAT_DEPTH = 0.05;
    /** How fast a ring climbs back up, in blocks per tick - a touch quicker than water (0.33 b/s). */
    private static final double RISE_SPEED = 0.025;
    /**
     * Extra drag on a ring that is still going down. Vanilla's own lava drag already slows the
     * plunge, this just keeps it short: lava is thicker than water and a ring should not end up
     * twenty blocks down a lava sea before it starts coming back.
     */
    private static final double SINK_DRAG = 0.9;

    /**
     * Step one: the Wither dies, the ring swallows its death flame. Only one ring per Wither: if
     * the player carries several waiting broken rings, the first one found is charged.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().getType() != EntityType.WITHER) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack ring = findWaitingRing(player);
        if (ring.isEmpty()) {
            return;
        }

        ring.set(ModDataComponents.WITHER_CHARGED.get(), Unit.INSTANCE);

        ServerLevel level = player.serverLevel();
        // The soul fire of the Wither itself: the ring has taken the flame that killed it.
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 0.8, player.getZ(),
                28, 0.5, 0.7, 0.5, 0.06);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 0.9F, 0.7F);
        player.displayClientMessage(
                Component.translatable(WITHER_CHARGED_MESSAGE).withStyle(ChatFormatting.GRAY), true);

        FlightRingMod.LOGGER.debug("[FlightRing] {} fed a Wither's death flame to the infernal ring",
                player.getName().getString());
    }

    /**
     * Step two, called every tick for every item entity on both sides (see
     * {@code InfernalRingLavaMixin}).
     * <p>
     * A ring in lava first behaves like an item thrown into water: it goes under with the speed it
     * arrived at, the fluid slows it down, and then it floats back up - only a touch quicker than
     * water, because lava would otherwise keep a ring under an opaque surface for half a minute.
     * Once it is back up it parks just under the fluid's top and stays there until someone picks
     * it up.
     * <p>
     * The one piece of state this needs is {@code noGravity}, which is used as "the lava has taken
     * this ring over": it is still false while the ring is on its way in (so the plunge is left to
     * the fluid), and it is set the moment the plunge is over. From then on it also does the work
     * of keeping a parked ring from bobbing: a floating item normally gets gravity applied again as
     * soon as it reaches the surface, which is what makes it bob on water - a ring held by lava
     * does not. It is cleared again the moment the ring is not in lava any more, so nothing else
     * about the item is ever affected.
     * <p>
     * The first time a CHARGED broken ring touches lava the quench happens: the item entity takes
     * the working ring instead of the broken one, so what sinks in and floats back out is already
     * the finished ring. The swap is a server decision only - the item entity syncs its stack to
     * every client by itself.
     *
     * @return true while the ring is in the lava, in which case the caller keeps it from expiring:
     *     it waits for its owner however long that takes.
     */
    public static boolean tickInLava(ItemEntity entity) {
        ItemStack stack = entity.getItem();
        if (!isInfernalRing(stack)) {
            return false;
        }
        Level level = entity.level();
        if (!level.getFluidState(entity.blockPosition()).is(FluidTags.LAVA)) {
            // Back out of the lava: nothing is holding the ring up any more.
            if (entity.isNoGravity()) {
                entity.setNoGravity(false);
            }
            return false;
        }

        if (!level.isClientSide && isWaitingForQuench(stack)) {
            entity.setItem(new ItemStack(ModItems.RELIC_RINGS.get(RelicRing.INFERNAL).get()));
            quench(level, entity);
        }

        double depth = entity.getFluidHeight(FluidTags.LAVA);
        Vec3 motion = entity.getDeltaMovement();

        if (!entity.isNoGravity()) {
            if (motion.y < 0.0) {
                // Still on the way in. Vanilla's lava drag does most of the work, the extra drag
                // only keeps the plunge short. No parking here: this is the part that has to look
                // like an item going into water.
                entity.setDeltaMovement(motion.x * SINK_DRAG, motion.y * SINK_DRAG, motion.z * SINK_DRAG);
                return true;
            }
            // The fluid has stopped the plunge: from here on the lava carries the ring.
            entity.setNoGravity(true);
        }

        if (depth > SURFACE_LEVEL) {
            // On the way back up, a touch quicker than an item floating up in water.
            entity.setDeltaMovement(motion.x * 0.99, RISE_SPEED, motion.z * 0.99);
            return true;
        }
        // Up at the surface: parked with its bottom FLOAT_DEPTH under the fluid's top, and held
        // there until it is picked up.
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setPos(entity.getX(), entity.getY() + depth - FLOAT_DEPTH, entity.getZ());
        return true;
    }

    /** The quench itself: the ring comes up out of the lava as the working ring. */
    private static void quench(Level level, ItemEntity entity) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        server.sendParticles(ModParticles.SOUL_FLAME.get(),
                entity.getX(), entity.getY() + 0.2, entity.getZ(), 24, 0.25, 0.25, 0.25, 0.02);
        server.playSound(null, entity.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS, 1.0F, 0.7F);
        if (entity.getOwner() instanceof ServerPlayer thrower) {
            thrower.displayClientMessage(Component.translatable(QUENCHED_MESSAGE), true);
        }
        FlightRingMod.LOGGER.debug("[FlightRing] the infernal ring was quenched into the working ring at {}",
                entity.blockPosition());
    }

    /** True for both forms of the infernal ring: the working one and its broken form. */
    public static boolean isInfernalRing(ItemStack stack) {
        return stack.is(ModItems.RELIC_RINGS.get(RelicRing.INFERNAL).get())
                || (stack.getItem() instanceof DamagedRingItem damaged
                        && damaged.relic() == RelicRing.INFERNAL);
    }

    /** A broken infernal ring that swallowed a Wither's death flame and now wants the lava. */
    private static boolean isWaitingForQuench(ItemStack stack) {
        return stack.getItem() instanceof DamagedRingItem damaged
                && damaged.relic() == RelicRing.INFERNAL
                && DamagedRingItem.isWitherCharged(stack);
    }

    /**
     * The carried broken infernal ring that is still asleep, or an empty stack. Only this one
     * stack is charged: one Wither = one ring, even when the player carries several.
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
                && damaged.relic() == RelicRing.INFERNAL
                && !DamagedRingItem.isWitherCharged(stack);
    }

    private InfernalRingQuest() {
    }
}
