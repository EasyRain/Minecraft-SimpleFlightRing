package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Miner's Veteran" - the special ability of the miner relic ring. Like every other
 * ability it only works while the ring is worn (in the Curios slot) and still has
 * durability left.
 * <ol>
 *   <li><b>Night Vision</b> forever: re-applied every second with a 3 second duration, so
 *       it never runs out but also disappears within 3 s of taking the ring off.</li>
 *   <li><b>Haste by depth</b> (same 3 s / every second refresh): level 1 below sea level
 *       (y &lt; 63), level 2 below y = 0, level 3 below y = -32, nothing above sea level.
 *       Outside the overworld height is ignored and it is always level 1.</li>
 *   <li><b>Immune to TNT blasts</b> - only TNT (and TNT minecarts); creepers, beds and
 *       every other explosion still hurt normally.</li>
 *   <li><b>TNT blast on the ability key</b>: a vanilla TNT explosion (radius 4, breaks
 *       blocks) centred on the wearer, 3 s cooldown. The wearer survives it thanks to the
 *       immunity above, and the 力量 (Power) enchantment scales the damage it deals.</li>
 * </ol>
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class MinerVeteranAbility {

    /** Passive: both buffs last 3 s and are re-applied once per second. */
    private static final int BUFF_DURATION_TICKS = 60;
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

    // ------------------------------------------------------------------
    // Passive: Night Vision + depth scaled Haste
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().getGameTime() % BUFF_REFRESH_TICKS != 0) {
            return;                          // once per second is enough for a 3 s buff
        }
        if (wornRing(player).isEmpty()) {
            return;
        }
        player.addEffect(hiddenBuff(MobEffects.NIGHT_VISION, 0));
        int haste = hasteAmplifier(player);
        if (haste >= 0) {
            player.addEffect(hiddenBuff(MobEffects.HASTE, haste));
        }
    }

    /**
     * A buff with no particles and no HUD icon: it is re-applied every second forever while
     * the ring is worn, so an icon would only flicker.
     */
    private static MobEffectInstance hiddenBuff(Holder<MobEffect> effect, int amplifier) {
        return new MobEffectInstance(effect, BUFF_DURATION_TICKS, amplifier, true, false, false);
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
    // Passive: TNT immunity
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || wornRing(player).isEmpty()) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        if (direct != null && (direct.getType() == EntityType.TNT || direct.getType() == EntityType.TNT_MINECART)) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------
    // Active: the ability key detonates a TNT blast at the wearer
    // ------------------------------------------------------------------

    static void tryDetonate(ServerPlayer player, ItemStack ring) {
        long now = player.level().getGameTime();
        Long readyAt = DETONATE_READY_AT.get(player.getUUID());
        if (readyAt != null && now < readyAt) {
            long seconds = Math.max(1L, (readyAt - now + 19L) / 20L);
            player.sendOverlayMessage(Component.translatable(
                    "message.simpleflightring.miner_veteran_cooldown", seconds).withStyle(ChatFormatting.GRAY));
            return;
        }
        DETONATE_READY_AT.put(player.getUUID(), now + DETONATE_COOLDOWN_TICKS);

        ServerLevel level = player.level();
        // A TNT entity that is never added to the world: it only gives the explosion the TNT
        // damage source (so this ring's wearer stays immune to their own blast) while the
        // custom calculator scales the damage with the 力量 (Power) enchantment.
        PrimedTnt source = new PrimedTnt(level, player.getX(), player.getY(), player.getZ(), player);
        float multiplier = RingAbilities.abilityDamageMultiplier(ring);
        level.explode(source, level.damageSources().explosion(source, player),
                new ExplosionDamageCalculator() {
                    @Override
                    public float getEntityDamageAmount(Explosion explosion, Entity entity, float base) {
                        return super.getEntityDamageAmount(explosion, entity, base) * multiplier;
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
        DETONATE_READY_AT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        DETONATE_READY_AT.remove(event.getOriginal().getUUID());
    }

    private MinerVeteranAbility() {
    }
}
