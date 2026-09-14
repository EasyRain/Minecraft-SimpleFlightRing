package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Miner's Veteran" - the special ability of the miner relic ring. Like every other
 * ability it only works while the ring is worn (in the Curios slot) and still has
 * durability left.
 * <ol>
 *   <li><b>Night Vision</b> forever: re-applied every second. The buff lasts 30 s so the
 *       HUD icon is visible without ever getting into the "last 10 seconds" blinking
 *       state; taking the ring off (or draining it) removes it right away.</li>
 *   <li><b>Haste by depth</b> (same refresh): level 1 below sea level (y &lt; 63), level 2
 *       below y = 0, level 3 below y = -32, none above sea level. Outside the overworld
 *       height is ignored and it is always level 1.</li>
 *   <li><b>Immune to TNT blasts</b> - the damage AND the knockback of TNT (and TNT
 *       minecarts) only; creepers, respawn anchors, beds and every other explosion still
 *       hurt and push normally.</li>
 *   <li><b>TNT blast on the ability key</b>: a vanilla TNT explosion (radius 4, breaks
 *       blocks) centred on the wearer, 3 s cooldown. The wearer survives it thanks to the
 *       immunity above, and the 力量 (Power) enchantment scales the damage it deals.</li>
 * </ol>
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class MinerVeteranAbility {

    /**
     * Passive: the buffs last 30 s and are re-applied every second. 30 s is well past the
     * 10 s mark where vanilla starts blinking the HUD icon, so the icon reads as "always on"
     * instead of flickering, while still fading within half a minute of losing the ring.
     */
    private static final int BUFF_DURATION_TICKS = 600;
    private static final int BUFF_REFRESH_TICKS = 20;
    /** Passive: sea level and the two deeper steps of Haste. */
    private static final int SEA_LEVEL = 63;
    private static final int HASTE_STEP_2_Y = 0;
    private static final int HASTE_STEP_3_Y = -32;
    /** Active: radius of the TNT blast (vanilla TNT uses 4.0). */
    private static final float BLAST_RADIUS = 4.0F;
    /** Active: cooldown of the ability key. */
    private static final int DETONATE_COOLDOWN_TICKS = 60;

    private static final Map<UUID, Long> DETONATE_READY_AT = new HashMap<>();
    /** Players currently carrying the miner buffs, so losing the ring clears them once. */
    private static final Set<UUID> BUFFED = new HashSet<>();

    // ------------------------------------------------------------------
    // Passive: Night Vision + depth scaled Haste
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (wornRing(player).isEmpty()) {
            // Ring taken off (or drained): drop both buffs once instead of waiting them out.
            if (BUFFED.remove(player.getUUID())) {
                player.removeEffect(MobEffects.NIGHT_VISION);
                player.removeEffect(MobEffects.DIG_SPEED);
            }
            return;
        }
        if (player.level().getGameTime() % BUFF_REFRESH_TICKS != 0) {
            return;                          // once per second is plenty for a 30 s buff
        }
        applyBuff(player, MobEffects.NIGHT_VISION, 0);
        applyBuff(player, MobEffects.DIG_SPEED, hasteAmplifier(player));
        BUFFED.add(player.getUUID());
    }

    /**
     * Keeps one buff at the wanted level: {@code amplifier < 0} means "no buff at all"
     * (above sea level for Haste). A stronger buff that is already on the player is removed
     * first, otherwise vanilla would keep the higher level until it expires - the player
     * would stay at Haste III after climbing back up.
     */
    private static void applyBuff(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        if (amplifier < 0) {
            if (player.hasEffect(effect)) {
                player.removeEffect(effect);
            }
            return;
        }
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getAmplifier() > amplifier) {
            player.removeEffect(effect);
        }
        // ambient (no particles), showIcon: the player should be able to see that it is on.
        player.addEffect(new MobEffectInstance(effect, BUFF_DURATION_TICKS, amplifier, true, false, true));
    }

    /**
     * Haste amplifier by depth: 0 (level I) below sea level, 1 below y = 0, 2 below y = -32,
     * or {@code -1} for "no haste" above sea level. Outside the overworld the height does not
     * matter and the amplifier is always 0.
     */
    private static int hasteAmplifier(Player player) {
        if (player.level().dimension() != Level.OVERWORLD) {
            return 0;
        }
        double y = player.getY();
        if (y < HASTE_STEP_3_Y) {
            return 2;
        }
        if (y < HASTE_STEP_2_Y) {
            return 1;
        }
        return y < SEA_LEVEL ? 0 : -1;
    }

    // ------------------------------------------------------------------
    // Passive: TNT immunity (damage and knockback)
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || wornRing(player).isEmpty()) {
            return;
        }
        if (isTntBlast(event.getSource())) {
            event.setCanceled(true);
        }
    }

    /**
     * Cancels the shove of a TNT blast as well: vanilla applies the knockback outside the
     * damage call, so cancelling the damage alone still threw the wearer around.
     */
    @SubscribeEvent
    public static void onExplosionKnockback(ExplosionKnockbackEvent event) {
        if (!(event.getAffectedEntity() instanceof Player player) || wornRing(player).isEmpty()) {
            return;
        }
        if (isTntBlast(event.getExplosion())) {
            event.setKnockbackVelocity(Vec3.ZERO);
        }
    }

    /**
     * True only for an explosion that came from a TNT (or a TNT minecart): the damage must
     * be tagged as an explosion AND its direct source entity must be that TNT. Anything else -
     * creepers, respawn anchors, beds, end crystals - is left completely alone.
     */
    private static boolean isTntBlast(DamageSource source) {
        if (!source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        return isTnt(source.getDirectEntity());
    }

    private static boolean isTntBlast(Explosion explosion) {
        return isTnt(explosion.getDirectSourceEntity());
    }

    private static boolean isTnt(Entity entity) {
        return entity != null
                && (entity.getType() == EntityType.TNT || entity.getType() == EntityType.TNT_MINECART);
    }

    // ------------------------------------------------------------------
    // Active: the ability key detonates a TNT blast at the wearer
    // ------------------------------------------------------------------

    static void tryDetonate(ServerPlayer player, ItemStack ring) {
        long now = player.serverLevel().getGameTime();
        Long readyAt = DETONATE_READY_AT.get(player.getUUID());
        if (readyAt != null && now < readyAt) {
            long seconds = Math.max(1L, (readyAt - now + 19L) / 20L);
            player.displayClientMessage(Component.translatable(
                    "message.simpleflightring.miner_veteran_cooldown", seconds).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        DETONATE_READY_AT.put(player.getUUID(), now + DETONATE_COOLDOWN_TICKS);

        ServerLevel level = player.serverLevel();
        // A TNT entity that is never added to the world: it only gives the explosion the TNT
        // damage source (so this ring's wearer stays immune to their own blast) while the
        // custom calculator scales the damage with the 力量 (Power) enchantment.
        PrimedTnt source = new PrimedTnt(level, player.getX(), player.getY(), player.getZ(), player);
        float multiplier = RingAbilities.abilityDamageMultiplier(ring);
        level.explode(source, level.damageSources().explosion(source, player),
                new ExplosionDamageCalculator() {
                    @Override
                    public float getEntityDamageAmount(Explosion explosion, Entity entity) {
                        return super.getEntityDamageAmount(explosion, entity) * multiplier;
                    }
                },
                player.getX(), player.getY(), player.getZ(), BLAST_RADIUS, false, Level.ExplosionInteraction.TNT);

        FlightRingMod.LOGGER.debug("[FlightRing] {} detonated the miner blast (x{})",
                player.getName().getString(), multiplier);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** The worn miner ring, or an empty stack: needs the ability and durability left. */
    private static ItemStack wornRing(Player player) {
        if (!CuriosCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        if (ring.getItem() instanceof FlightRingItem item
                && item.hasAbility(RingAbility.MINER_VETERAN)
                && item.isUsable(ring)) {
            return ring;
        }
        return ItemStack.EMPTY;
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
        DETONATE_READY_AT.remove(playerId);
        BUFFED.remove(playerId);
    }

    private MinerVeteranAbility() {
    }
}
