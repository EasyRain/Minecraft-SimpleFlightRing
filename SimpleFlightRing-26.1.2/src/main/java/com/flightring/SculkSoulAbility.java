package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Sculk Soul" - the special ability of the sculk relic ring. Like every other ability it
 * only works while the ring is worn in the Curios "flight ring" slot.
 * <ol>
 *   <li><b>Darkness immunity</b>: Blindness and Darkness can never be applied
 *       ({@link MobEffectEvent.Applicable}).</li>
 *   <li><b>Wardens see you as one of their own</b>: a warden is not allowed to target the
 *       wearer unless the wearer attacked it first ({@link LivingChangeTargetEvent}).
 *       Attacking a warden is remembered for {@link #ANGER_MEMORY_TICKS}, so it still
 *       fights back.</li>
 *   <li><b>Silence in the deep dark</b>: while the wearer stands in the deep dark biome,
 *       their sound events and game events (vibrations - what sculk sensors and shriekers
 *       listen to) are cancelled, plus the sounds played right at their position.</li>
 *   <li><b>Sonic boom on the ability key</b> ({@link #tryFireSonicBoom}): a warden-style
 *       beam along the player's line of sight, 10 damage, vanilla knockback, 3 s cooldown.</li>
 * </ol>
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class SculkSoulAbility {

    /** Effect 4: internal cooldown of the sonic boom. */
    private static final int SONIC_BOOM_COOLDOWN_TICKS = 60;
    /** Effect 4: how far the beam reaches (vanilla warden range is 15 xz / 20 y). */
    private static final double SONIC_BOOM_RANGE = 20.0;
    /** Effect 4: vanilla warden sonic boom damage. */
    private static final float SONIC_BOOM_DAMAGE = 10.0F;
    /** Effect 4: vanilla warden knockback, before the target's knockback resistance. */
    private static final double SONIC_BOOM_KNOCKBACK_HORIZONTAL = 2.5;
    private static final double SONIC_BOOM_KNOCKBACK_VERTICAL = 0.5;
    /** Effect 3: sounds played within this distance of the wearer are silenced too. */
    private static final double SILENCE_RADIUS = 4.0;

    /** Effect 2: how long attacking a warden keeps it allowed to fight back. */
    private static final long ANGER_MEMORY_TICKS = 200L;

    private static final Map<UUID, Long> SONIC_BOOM_READY_AT = new HashMap<>();
    private static final Map<UUID, Long> ANGERED_WARDEN_AT = new HashMap<>();

    // ------------------------------------------------------------------
    // Effect 1: no Blindness / Darkness
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player) || !wearsSculkRing(player)) {
            return;
        }
        var effect = event.getEffectInstance().getEffect();
        if (effect.is(MobEffects.BLINDNESS) || effect.is(MobEffects.DARKNESS)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /**
     * Blindness and Darkness that were already on the player when the ring was equipped
     * must go as well: the event above only stops new ones.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !wearsSculkRing(player)) {
            return;
        }
        if (player.hasEffect(MobEffects.BLINDNESS)) {
            player.removeEffect(MobEffects.BLINDNESS);
        }
        if (player.hasEffect(MobEffects.DARKNESS)) {
            player.removeEffect(MobEffects.DARKNESS);
        }
    }

    // ------------------------------------------------------------------
    // Effect 2: wardens are neutral (but still hit back)
    // ------------------------------------------------------------------

    /** Remembers that the player attacked a warden, so that warden may retaliate. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().getType() != EntityType.WARDEN) {
            return;
        }
        if (event.getSource().getEntity() instanceof Player player && wearsSculkRing(player)) {
            ANGERED_WARDEN_AT.put(player.getUUID(), event.getEntity().level().getGameTime());
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity().getType() != EntityType.WARDEN) {
            return;
        }
        if (!(event.getNewAboutToBeSetTarget() instanceof Player player) || !wearsSculkRing(player)) {
            return;
        }
        long now = player.level().getGameTime();
        Long angeredAt = ANGERED_WARDEN_AT.get(player.getUUID());
        if (angeredAt != null && now - angeredAt <= ANGER_MEMORY_TICKS) {
            return; // the wearer picked that fight: let the warden answer
        }
        event.setNewAboutToBeSetTarget(null);
    }

    // ------------------------------------------------------------------
    // Effect 3: silence in the deep dark
    // ------------------------------------------------------------------

    /** Vibrations (sculk sensors, shriekers, warden hearing) caused by the wearer. */
    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        if (!(event.getCause() instanceof Player player)) {
            return;
        }
        if (wearsSculkRing(player) && isInDeepDark(player)) {
            event.setCanceled(true);
        }
    }

    /** Sounds attached to the wearer (footsteps, eating, ...). */
    @SubscribeEvent
    public static void onSoundAtEntity(PlayLevelSoundEvent.AtEntity event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (wearsSculkRing(player) && isInDeepDark(player)) {
            event.setCanceled(true);
        }
    }

    /** Positional sounds (breaking and placing blocks), when they happen at the wearer. */
    @SubscribeEvent
    public static void onSoundAtPosition(PlayLevelSoundEvent.AtPosition event) {
        if (event.getSource() != SoundSource.PLAYERS && event.getSource() != SoundSource.BLOCKS) {
            return;
        }
        Vec3 position = event.getPosition();
        for (Player player : event.getLevel().players()) {
            if (!wearsSculkRing(player) || !isInDeepDark(player)) {
                continue;
            }
            if (player.position().distanceToSqr(position) <= SILENCE_RADIUS * SILENCE_RADIUS) {
                event.setCanceled(true);
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Effect 4: the sonic boom (ability key)
    // ------------------------------------------------------------------

    /**
     * Fires the warden-style sonic boom for the worn sculk ring, if it is off cooldown.
     * Called by {@link RingAbilityKeyHandler} when the ability key is pressed.
     */
    static void tryFireSonicBoom(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long readyAt = SONIC_BOOM_READY_AT.get(player.getUUID());
        if (readyAt != null && now < readyAt) {
            long seconds = Math.max(1L, (readyAt - now + 19L) / 20L);
            player.sendOverlayMessage(Component.translatable(
                    "message.simpleflightring.sculk_soul_cooldown", seconds).withStyle(ChatFormatting.GRAY));
            return;
        }
        SONIC_BOOM_READY_AT.put(player.getUUID(), now + SONIC_BOOM_COOLDOWN_TICKS);
        fireSonicBoom(player);
    }

    private static void fireSonicBoom(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 from = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 to = from.add(direction.scale(SONIC_BOOM_RANGE));

        // Stop the beam at the first block, like the warden's line of sight.
        BlockHitResult blockHit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            to = blockHit.getLocation();
        }

        LivingEntity target = findTarget(level, player, from, to);
        Vec3 beamEnd = target == null ? to : target.getEyePosition();
        Vec3 beam = beamEnd.subtract(from);
        double length = beam.length();
        if (length < 0.001) {
            beamEnd = from.add(direction);
            beam = beamEnd.subtract(from);
            length = beam.length();
        }
        Vec3 step = beam.normalize();

        // Vanilla draws one SONIC_BOOM particle per block of the beam, plus a few extra.
        int particles = (int) Math.floor(length) + 7;
        for (int i = 1; i < particles; i++) {
            Vec3 point = from.add(step.scale(i));
            level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 1.0F);

        if (target == null) {
            return;
        }
        if (target.hurtServer(level, level.damageSources().sonicBoom(player), SONIC_BOOM_DAMAGE)) {
            double resistance = 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            target.push(step.x * SONIC_BOOM_KNOCKBACK_HORIZONTAL * resistance,
                    step.y * SONIC_BOOM_KNOCKBACK_VERTICAL * resistance,
                    step.z * SONIC_BOOM_KNOCKBACK_HORIZONTAL * resistance);
        }
        FlightRingMod.LOGGER.debug("[FlightRing] {} fired the sculk sonic boom at {}",
                player.getName().getString(), target.getName().getString());
    }

    /** First living entity the beam runs into (the player itself is skipped). */
    private static LivingEntity findTarget(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
        List<Entity> candidates = level.getEntities(player, new AABB(from, to).inflate(1.0),
                entity -> entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator());
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3 direction = to.subtract(from).normalize();
        double reach = from.distanceTo(to) + 1.0;
        for (Entity candidate : candidates) {
            LivingEntity living = (LivingEntity) candidate;
            Vec3 offset = living.getBoundingBox().getCenter().subtract(from);
            double along = offset.dot(direction);
            if (along < 0.0 || along > reach) {
                continue;
            }
            double miss = offset.subtract(direction.scale(along)).length();
            if (miss <= living.getBbWidth() * 0.5 + 0.4 && along < bestDistance) {
                bestDistance = along;
                best = living;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** True while the player wears a ring with the sculk soul in the Curios slot. */
    private static boolean wearsSculkRing(Player player) {
        if (!CuriosCompat.isLoaded()) {
            return false;
        }
        var ring = CuriosCompat.findRingInSlot(player);
        return ring.getItem() instanceof FlightRingItem item && item.hasAbility(RingAbility.SCULK_SOUL);
    }

    private static boolean isInDeepDark(Player player) {
        BlockPos pos = player.blockPosition();
        return player.level().getBiome(pos).is(Biomes.DEEP_DARK);
    }

    /**
     * Called from the mixins - the two places vanilla does NOT go through an event:
     * {@code SculkShriekerBlockEntity#tryShriek} (standing on a shrieker calls it directly)
     * and {@code Warden#canTargetEntity} (the warden picks its target through brain memory).
     */

    /** True when the wearer must be silent: ring worn and standing in the deep dark. */
    public static boolean isSilenced(Player player) {
        return wearsSculkRing(player) && isInDeepDark(player);
    }

    /**
     * False for a ring wearer the warden must not notice at all. Attacking a warden is
     * remembered for {@link #ANGER_MEMORY_TICKS}, so it still gets to fight back.
     */
    public static boolean wardenMayTarget(Entity target) {
        if (!(target instanceof Player player) || !wearsSculkRing(player)) {
            return true;
        }
        Long angeredAt = ANGERED_WARDEN_AT.get(player.getUUID());
        if (angeredAt != null && player.level().getGameTime() - angeredAt <= ANGER_MEMORY_TICKS) {
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        clear(event.getOriginal().getUUID());
    }

    private static void clear(UUID playerId) {
        SONIC_BOOM_READY_AT.remove(playerId);
        ANGERED_WARDEN_AT.remove(playerId);
    }

    private SculkSoulAbility() {
    }
}
