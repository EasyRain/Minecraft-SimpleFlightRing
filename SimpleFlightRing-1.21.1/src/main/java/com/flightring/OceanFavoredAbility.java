package com.flightring;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Ocean's Favourite" - the ability of the ocean relic ring. Passive, and like every other
 * relic ability it only works while the ring is worn in the Curios slot with durability left.
 * <ol>
 *   <li><b>Water breathing and dolphin's grace forever</b>: re-applied every second, 30 s
 *       buffs so the HUD icons stay readable; taking the ring off clears them right away.</li>
 *   <li><b>Night vision and clear water while submerged</b>: the buff only while
 *       {@code isInWater()}, and the water fog is removed client side
 *       (see {@code OceanFogHandler}).</li>
 *   <li><b>The sea's children respect the wearer</b>: monsters in the aquatic hostiles tag
 *       (drowned, guardians) may not target the wearer - but a wearer who hits one is
 *       remembered, and that one keeps fighting back until it dies or the wearer leaves its
 *       follow range (the same rule the sculk ring uses for wardens).</li>
 *   <li><b>Half damage in the ocean</b>: any damage taken while standing in an ocean biome is
 *       halved.</li>
 *   <li><b>Whirlpool</b>: every enemy within 10 blocks is slowed - Slowness I, Slowness II
 *       while it rains on the wearer, Slowness III in an ocean biome.</li>
 *   <li><b>Flames go out on their own</b>: checked once a second.</li>
 * </ol>
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class OceanFavoredAbility {

    /**
     * The buffs last 30 s and are re-applied every second - the same numbers the miner ring
     * uses, so the icons read as "always on" instead of blinking.
     */
    private static final int BUFF_DURATION_TICKS = 600;
    private static final int BUFF_REFRESH_TICKS = 20;
    /** Every enemy within this radius of the wearer is caught in the whirlpool. */
    private static final double WHIRLPOOL_RADIUS = 10.0;
    /** Refreshed every second, so it lingers for two seconds after a mob leaves the radius. */
    private static final int WHIRLPOOL_DURATION_TICKS = 60;
    private static final int WHIRLPOOL_AMPLIFIER = 0;
    private static final int WHIRLPOOL_RAIN_AMPLIFIER = 1;
    private static final int WHIRLPOOL_OCEAN_AMPLIFIER = 2;
    /** Damage taken in an ocean biome is halved. */
    private static final float OCEAN_DAMAGE_MULTIPLIER = 0.5F;
    /**
     * The submerged mining speed multiplier the ring aims for: 1.0 means "no slow-down at all".
     * The Curios modifier adds the difference to vanilla's 0.2 base (see {@code CuriosCompat}),
     * and {@link #onBreakSpeed} clamps the total back to this value so other mods cannot stack
     * on the same attribute.
     */
    static final double SUBMERGED_MINING_SPEED_CLAMP = 1.0;
    /** Vanilla's base value of {@code SUBMERGED_MINING_SPEED} (see {@code Player#getDigSpeed}). */
    private static final double SUBMERGED_MINING_SPEED_BASE = 0.2;

    /** What the Curios attribute modifier has to add to reach {@link #SUBMERGED_MINING_SPEED_CLAMP}. */
    static double submergedMiningBonus() {
        return SUBMERGED_MINING_SPEED_CLAMP - SUBMERGED_MINING_SPEED_BASE;
    }

    /**
     * The aquatic monsters that treat the wearer as one of their own. A data driven entity type
     * tag - the vanilla {@code #minecraft:aquatic} tag is about water dwelling animals and does
     * <b>not</b> contain the drowned - so a pack can extend the list without touching this mod.
     */
    private static final TagKey<EntityType<?>> AQUATIC_HOSTILES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "aquatic_hostiles"));

    /** Players currently carrying the sea's blessing, so losing the ring clears it once. */
    private static final Set<UUID> BUFFED = new HashSet<>();
    /** The aquatic monsters each wearer has provoked, per player. */
    private static final Map<UUID, Set<UUID>> PROVOKED = new HashMap<>();

    // ------------------------------------------------------------------
    // Passives on the player: buffs, night vision, dry flames, whirlpool
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (wornRing(player).isEmpty()) {
            if (BUFFED.remove(player.getUUID())) {
                clearBuffs(player);
            }
            return;
        }
        if (player.level().getGameTime() % BUFF_REFRESH_TICKS != 0) {
            return;                          // once per second is plenty for a 30 s buff
        }
        applyBuff(player, MobEffects.WATER_BREATHING, 0);
        applyBuff(player, MobEffects.DOLPHINS_GRACE, 0);
        if (player.isInWater()) {
            applyBuff(player, MobEffects.NIGHT_VISION, 0);
        } else if (player.hasEffect(MobEffects.NIGHT_VISION)) {
            // Night vision belongs to the water: drop it as soon as the wearer surfaces.
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
        extinguish(player);
        applyWhirlpool(player);
        BUFFED.add(player.getUUID());
    }

    /**
     * Keeps one buff at the wanted level. A stronger instance already on the player is removed
     * first, so the level really drops when it has to - the same guard the miner ring uses.
     */
    private static void applyBuff(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getAmplifier() > amplifier) {
            player.removeEffect(effect);
        }
        // ambient (no particles), showIcon: the player should see that it is on.
        player.addEffect(new MobEffectInstance(effect, BUFF_DURATION_TICKS, amplifier, true, false, true));
    }

    private static void clearBuffs(ServerPlayer player) {
        player.removeEffect(MobEffects.WATER_BREATHING);
        player.removeEffect(MobEffects.DOLPHINS_GRACE);
        player.removeEffect(MobEffects.NIGHT_VISION);
    }

    /** The sea does not let its favourite burn: checked once a second. */
    private static void extinguish(ServerPlayer player) {
        if (!player.isOnFire()) {
            return;
        }
        player.clearFire();
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 1.0, player.getZ(),
                8, 0.3, 0.4, 0.3, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    /**
     * The whirlpool: every enemy within 10 blocks is slowed. Ocean biome gives Slowness III,
     * rain gives Slowness II, otherwise Slowness I. The effect is short and refreshed every
     * second, so it fades on its own once a mob leaves the radius.
     */
    private static void applyWhirlpool(ServerPlayer player) {
        int amplifier = whirlpoolAmplifier(player);
        AABB box = player.getBoundingBox().inflate(WHIRLPOOL_RADIUS);
        List<Entity> caught = player.serverLevel().getEntities(player, box,
                entity -> entity instanceof Enemy && entity.isAlive());
        for (Entity entity : caught) {
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        WHIRLPOOL_DURATION_TICKS, amplifier, false, true, true));
            }
        }
    }

    private static int whirlpoolAmplifier(ServerPlayer player) {
        if (isInOcean(player)) {
            return WHIRLPOOL_OCEAN_AMPLIFIER;
        }
        return player.serverLevel().isRainingAt(player.blockPosition())
                ? WHIRLPOOL_RAIN_AMPLIFIER
                : WHIRLPOOL_AMPLIFIER;
    }

    // ------------------------------------------------------------------
    // Half damage in the ocean
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || wornRing(player).isEmpty()) {
            return;
        }
        if (isInOcean(player)) {
            event.setAmount(event.getAmount() * OCEAN_DAMAGE_MULTIPLIER);
        }
    }

    private static boolean isInOcean(Player player) {
        return player.level().getBiome(player.blockPosition()).is(BiomeTags.IS_OCEAN);
    }

    // ------------------------------------------------------------------
    // No underwater mining slow-down (and no stacking on that attribute)
    // ------------------------------------------------------------------

    /**
     * Vanilla multiplies the mining speed by {@code SUBMERGED_MINING_SPEED} (0.2 by default)
     * whenever the eyes are in water. The ocean ring raises that attribute to 1.0 through a
     * Curios modifier (see {@code CuriosCompat}), which on its own already cancels the penalty.
     * <p>
     * Another mod adding its own modifier to the same attribute would stack on top of that and
     * let the wearer mine faster than on land, so this runs LAST (LOWEST priority) and divides
     * any excess back out: the effective multiplier is clamped to exactly 1.0. It is the same
     * "no stacking with other mods" guard {@code FlightHandler#onBreakSpeed} uses for the Flight
     * Stability enchantment, only exact here because our target value is known.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (wornRing(player).isEmpty() || !player.isEyeInFluid(FluidTags.WATER)) {
            return;
        }
        double submerged = player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
        if (submerged > SUBMERGED_MINING_SPEED_CLAMP + 1.0E-4) {
            event.setNewSpeed((float) (event.getNewSpeed() / submerged * SUBMERGED_MINING_SPEED_CLAMP));
        }
    }

    // ------------------------------------------------------------------
    // The sea's children are neutral (but still hit back)
    // ------------------------------------------------------------------

    /** Remembers WHICH aquatic monster the wearer attacked, so that one may retaliate. */
    @SubscribeEvent
    public static void onAquaticHurt(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!isAquaticHostile(victim)) {
            return;
        }
        if (event.getSource().getEntity() instanceof Player player && !wornRing(player).isEmpty()) {
            PROVOKED.computeIfAbsent(player.getUUID(), key -> new HashSet<>()).add(victim.getUUID());
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!isAquaticHostile(event.getEntity())) {
            return;
        }
        if (!(event.getNewAboutToBeSetTarget() instanceof Player player) || wornRing(player).isEmpty()) {
            return;
        }
        if (isProvokedBy(player, event.getEntity())) {
            return; // the wearer picked that fight: let it answer
        }
        // Clearing the target (instead of cancelling) also drops one the mob grabbed earlier.
        event.setNewAboutToBeSetTarget(null);
    }

    private static boolean isAquaticHostile(Entity entity) {
        // wrapAsHolder is the non-deprecated way in: EntityType#is(TagKey) was removed in 26.1.2
        // and is deprecated in 1.21.1, builtInRegistryHolder() is deprecated in both.
        return entity instanceof Enemy
                && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).is(AQUATIC_HOSTILES);
    }

    /**
     * The Elder Guardian's curse is not an attack: vanilla hands Mining Fatigue III (and the
     * curse sound) to every player within 50 blocks of it, no matter what the guardian is
     * targeting ({@code MobEffectUtil.addEffectToPlayersAround}). A wearer counts as one of the
     * sea's own, so a curse coming from an aquatic monster is refused. The event carries the
     * source entity, so potions, commands and other mods stay untouched.
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player) || wornRing(player).isEmpty()) {
            return;
        }
        if (!event.getEffectInstance().getEffect().is(MobEffects.DIG_SLOWDOWN)) {
            return;
        }
        Entity source = event.getEffectSource();
        if (source == null || !isAquaticHostile(source)) {
            return;
        }
        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    /** True while this very monster is hunting the wearer (attacked and still in range). */
    private static boolean isProvokedBy(Player player, Entity mob) {
        Set<UUID> provoked = PROVOKED.get(player.getUUID());
        if (provoked == null || !provoked.contains(mob.getUUID())) {
            return false;
        }
        if (!mob.isAlive() || mob.level() != player.level()
                || mob.distanceToSqr(player) > Math.pow(followRange(mob), 2.0)) {
            provoked.remove(mob.getUUID());      // out of range (or gone): no more hatred
            return false;
        }
        return true;
    }

    private static double followRange(Entity mob) {
        return mob instanceof LivingEntity living
                ? living.getAttributeValue(Attributes.FOLLOW_RANGE)
                : 16.0;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** The worn ocean ring, or an empty stack: needs the ability and durability left. */
    private static ItemStack wornRing(Player player) {
        if (!CuriosCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        if (ring.getItem() instanceof FlightRingItem item
                && item.hasAbility(RingAbility.OCEAN_FAVORED)
                && item.isUsable(ring)) {
            return ring;
        }
        return ItemStack.EMPTY;
    }

    /**
     * True while this player wears a usable ocean ring, {@code false} for a null player.
     * <p>
     * Used by the client side {@code ElderGuardianCurseMixin} to decline the guardian's curse
     * packet: the Curios slot is readable on both sides for the local player, so the client can
     * decide this on its own without a network round trip.
     */
    public static boolean isCurseProtected(Player player) {
        return player != null && !wornRing(player).isEmpty();
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
        BUFFED.remove(playerId);
        PROVOKED.remove(playerId);
    }

    private OceanFavoredAbility() {
    }
}
