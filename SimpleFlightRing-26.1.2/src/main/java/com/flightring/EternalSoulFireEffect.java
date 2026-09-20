package com.flightring;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Eternal Soul Fire" - the mark the infernal relic ring's Flame Lord leaves on everything
 * around the wearer (see {@link FlameLordAbility}). It is a custom
 * {@link MobEffectCategory#HARMFUL} effect rather than a real fire, so water cannot put it
 * out the way it puts out vanilla flames, and milk cannot wash it off either (see below).
 * <p>
 * What it does, every second:
 * <ol>
 *   <li><b>Burns for {@value #DAMAGE_PER_SECOND} damage</b>, multiplied by the damage factor of the
 *       ring that lit it. That factor is <b>the ring's Energy Burst enchantment only</b>
 *       ({@link RingAbilities#abilityDamageMultiplier}) - the effect level itself never adds
 *       damage, so a level V instance from {@code /effect give} hits exactly as hard as level I
 *       unless a ring with a stronger enchantment re-lights it. The damage uses
 *       {@code level.damageSources().magic()}, which neither Fire Resistance nor Fire Protection
 *       reduces.</li>
 *   <li><b>Never expires.</b> An instance that arrives with a limited duration - a command, a
 *       potion, another mod - is rewritten to {@link MobEffectInstance#INFINITE_DURATION} on the
 *       first tick.</li>
 *   <li><b>Is worn down by Fire Resistance.</b> Every tick of the effect that finds the victim
 *       under Fire Resistance eats that buff and drops the soul fire by one level, so a Fire
 *       Resistance potion is a step-by-step purification instead of a cure. Wearers of a working
 *       infernal ring are exempt: their Fire Resistance is the ring's own passive, re-applied
 *       every tick, and would otherwise be drunk dry instantly.</li>
 *   <li><b>Cannot touch the sea's own.</b> A creature wearing a usable ocean relic ring
 *       ({@link RingAbility#OCEAN_FAVORED}) takes no damage from it at all, and
 *       {@link #apply} refuses to light one up in the first place.</li>
 * </ol>
 * What does <b>not</b> end it: a bucket of milk. 26.1.2 hands milk a
 * {@code ClearAllStatusEffectsConsumeEffect}, and {@code MilkMixin} takes this mark off the entity
 * for the duration of that consume effect. {@code /effect clear} does work - the command calls
 * {@code removeAllEffects}/{@code removeEffect} directly and never goes through the consume effect
 * - so an operator always has a way out.
 */
public class EternalSoulFireEffect extends MobEffect {

    /** Damage the mark deals each second, at a ring damage factor of 1.0. */
    private static final float DAMAGE_PER_SECOND = 2.0F;
    /** {@link MobEffect#applyEffectTick} runs once every twenty ticks, i.e. once a second. */
    private static final int TICK_INTERVAL = 20;
    /**
     * How much Fire Resistance a creature needs left for the mark to be worn down by it: three
     * minutes less one second of slack, i.e. a real potion rather than another mod's ring topping the
     * buff up every tick. The slack matters because the buff is already counting down while the mark
     * tick fires: a potion drunk a moment ago sits just under 3600 ticks and still has to count.
     */
    private static final int PURIFY_MIN_DURATION_TICKS = 3 * 60 * 20 - 20;
    /** Damage factor used when nothing recorded one (e.g. the instance came from a command). */
    private static final float NO_RING_MULTIPLIER = 1.0F;

    /**
     * The damage factor each marked creature was lit with, so the ring's enchantment rather than
     * the effect level decides the damage. Filled by {@link #apply}, read by
     * {@link #applyEffectTick}, dropped when the mark runs out and flushed when the server stops.
     */
    private static final Map<UUID, Float> DAMAGE_MULTIPLIERS = new HashMap<>();

    public EternalSoulFireEffect() {
        // BENEFICIAL + soul fire blue: a "good" effect, so nothing treats it as a flame to douse.
        super(MobEffectCategory.HARMFUL, 0xFF3AD8FF);
    }

    /**
     * Lights {@code target} up with eternal soul fire of the given level, remembered together
     * with the damage factor of the ring that did it.
     * <p>
     * The level is a 1-based level (3 = amplifier 2, the level the Flame Lord's ability key
     * always applies). An instance already burning at a higher level is left at that level, an
     * instance with a limited duration is rewritten to an endless one, and a creature wearing a
     * working ocean relic ring is skipped entirely.
     *
     * @param target           the creature to mark
     * @param level            1-based effect level; levels above 1 are cosmetic here, they never
     *                         change the damage
     * @param damageMultiplier the lighting ring's {@link RingAbilities#abilityDamageMultiplier}
     */
    public static void apply(LivingEntity target, int level, float damageMultiplier) {
        if (target.level().isClientSide() || isImmune(target)) {
            return;
        }
        int wanted = Math.max(0, level - 1);
        MobEffectInstance current = target.getEffect(ModMobEffects.ETERNAL_SOUL_FIRE);
        if (current == null || current.getAmplifier() < wanted) {
            // addEffect already replaces a weaker instance; the multiplier is recorded first so
            // the very first tick of the new instance sees the new factor.
            DAMAGE_MULTIPLIERS.put(target.getUUID(), damageMultiplier);
            if (!target.addEffect(instance(wanted))) {
                DAMAGE_MULTIPLIERS.remove(target.getUUID());
            }
            return;
        }
        // Already burning at least as strongly: only the factor of whatever lit it is refreshed,
        // the duration is forced endless by the tick below.
        DAMAGE_MULTIPLIERS.put(target.getUUID(), damageMultiplier);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
        return tickCount % TICK_INTERVAL == 0;
    }

    /**
     * 26.1.2 hands the effect the server level it runs on
     * ({@code applyEffectTick(ServerLevel, LivingEntity, int)}); the level is only needed for the
     * damage source, which is reached through the victim itself.
     */
    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity target, int amplifier) {
        // The instance may have been granted by a command (or by another mod) with a fixed
        // duration, so the endless rewrite has to happen here as well as in apply().
        forceInfiniteDuration(target, amplifier);
        // The flame lord's own and the sea's own take no damage from the mark - but they are still
        // purified by a real Fire Resistance potion, so a wearer who was marked before putting the
        // ring on can drink their way out instead of having to take the ring off first. The ring's
        // own forty tick Fire Resistance never counts (see purifyFireResistance).
        if (!isImmune(target)) {
            Float recorded = DAMAGE_MULTIPLIERS.get(target.getUUID());
            float damage = DAMAGE_PER_SECOND * (recorded != null ? recorded : NO_RING_MULTIPLIER);
            // magic() ignores Fire Resistance and Fire Protection, so nothing but a ring stops this.
            target.hurtServer(level, target.damageSources().magic(), damage);
        }
        Mood mood = purifyFireResistance(target);
        if (mood == Mood.LEVEL_DROPPED) {
            // The mark was eaten down by a Fire Resistance potion: every level lost is one step
            // of the purification, so the agent is gone once the level reaches zero.
            levelDown(target, amplifier);
        }
        return true;
    }

    /** What {@link #purifyFireResistance} did, so the caller knows whether to lower the level. */
    private enum Mood { NOTHING, LEVEL_DROPPED }

    /**
     * A Fire Resistance potion wears the mark down instead of curing it: the buff is consumed and
     * the soul fire drops one level. A wearer of a working infernal ring is skipped, because the
     * ring's own passive Fire Resistance is re-applied every tick and would be stripped instantly.
     */
    private static Mood purifyFireResistance(LivingEntity target) {
        MobEffectInstance fireResistance = target.getEffect(MobEffects.FIRE_RESISTANCE);
        if (fireResistance == null) {
            return Mood.NOTHING;
        }
        // Only a real potion wears the mark down: it has to have at least three minutes to run.
        // Other mods' rings hand out short Fire Resistance and re-apply it endlessly, and draining
        // those one level a second would hand out a free purification. Endless instances are left
        // out for the same reason - they belong to a ring, not to a potion.
        if (fireResistance.getDuration() < PURIFY_MIN_DURATION_TICKS) {
            return Mood.NOTHING;
        }
        target.removeEffect(MobEffects.FIRE_RESISTANCE);
        return Mood.LEVEL_DROPPED;
    }

    /**
     * Removes the mark and immediately re-applies it one level weaker. An infinite instance can
     * never be overwritten by a weaker one through {@code addEffect}, so it has to be taken off
     * first. At level zero the mark is simply gone, exactly as a fully purified curse should be.
     */
    private static void levelDown(LivingEntity target, int amplifier) {
        target.removeEffect(ModMobEffects.ETERNAL_SOUL_FIRE);
        int next = amplifier - 1;
        if (next < 0) {
            DAMAGE_MULTIPLIERS.remove(target.getUUID());
            FlightRingMod.LOGGER.debug("[FlightRing] eternal soul fire on {} was fully purified",
                    target.getName().getString());
            return;
        }
        target.addEffect(instance(next));
        FlightRingMod.LOGGER.debug("[FlightRing] eternal soul fire on {} dropped to level {}",
                target.getName().getString(), next + 1);
    }

    /**
     * Rewrites a limited instance into an endless one. {@code MobEffectInstance} has no duration
     * setter, so the only way is to take the instance off and put an endless copy back with the
     * same amplifier and display flags; an instance that is already endless is left alone.
     */
    private static void forceInfiniteDuration(LivingEntity target, int amplifier) {
        MobEffectInstance current = target.getEffect(ModMobEffects.ETERNAL_SOUL_FIRE);
        if (current == null || current.isInfiniteDuration()) {
            return;
        }
        target.removeEffect(ModMobEffects.ETERNAL_SOUL_FIRE);
        target.addEffect(new MobEffectInstance(ModMobEffects.ETERNAL_SOUL_FIRE,
                MobEffectInstance.INFINITE_DURATION, amplifier, current.isAmbient(), current.isVisible(),
                current.showIcon()));
    }

    /** An endless soul fire instance of the given amplifier, visible in the HUD like any buff. */
    private static MobEffectInstance instance(int amplifier) {
        return new MobEffectInstance(ModMobEffects.ETERNAL_SOUL_FIRE,
                MobEffectInstance.INFINITE_DURATION, amplifier, false, true, true);
    }

    /**
     * True while this creature wears a usable ocean relic ring, i.e. while the sea's blessing
     * protects it from the mark. Only a real wearer counts: the ring has to sit in the Curios
     * flight ring slot and still have durability left, the same condition
     * {@code OceanFavoredAbility#wornRing} uses.
     */
    static boolean isOceanRingWearer(LivingEntity entity) {
        return entity instanceof Player player && hasUsableRing(player, RingAbility.OCEAN_FAVORED);
    }

    /**
     * True while this creature is one of the flame lord's own and cannot be marked at all: either
     * the sea's blessing ({@link RingAbility#OCEAN_FAVORED}) or a working infernal ring
     * ({@link RingAbility#FLAME_LORD}) protects it.
     */
    static boolean isImmune(LivingEntity entity) {
        return isOceanRingWearer(entity) || isInfernalRingWearer(entity);
    }

    /**
     * True while this creature wears a usable infernal relic ring, in which case the mark cannot
     * touch it at all - the flame lord's own are the last creatures its fire would burn.
     */
    static boolean isInfernalRingWearer(LivingEntity entity) {
        return entity instanceof Player player && hasUsableRing(player, RingAbility.FLAME_LORD);
    }

    private static boolean hasUsableRing(Player player, RingAbility ability) {
        if (!CuriosCompat.isLoaded()) {
            return false;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);

        return ring.getItem() instanceof FlightRingItem item
                && item.hasAbility(ability)
                && item.isUsable(ring);
    }

    /**
     * Registered by {@link FlightRingMod}: the recorded damage factors are keyed by entity UUID
     * and every world has its own, so a fresh server starts from an empty map. Loading a single
     * player world fires this too, which keeps the map from growing in single player sessions.
     */
    public static void onServerStopping(ServerStoppingEvent event) {
        DAMAGE_MULTIPLIERS.clear();
    }
}
