package com.flightring;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "New Hero" - the ability of the emerald relic ring. Purely passive, like the other
 * relic abilities it only works while the ring is worn in the Curios slot and still has
 * durability left.
 * <ol>
 *   <li><b>Hero of the Village forever</b>: re-applied every second, exactly like the
 *       miner ring's Night Vision. The buff lasts 30 s so the HUD icon is visible without
 *       blinking; taking the ring off (or draining it) removes it right away. Everything
 *       vanilla ties to the effect (villager discounts, raid rewards) follows from it.</li>
 *   <li><b>Double damage against illagers</b>: every damage the wearer deals to an entity
 *       in {@code minecraft:raiders} (pillager, vindicator, evoker, ravager, illusioner,
 *       witch) is doubled - melee, arrows and everything else that carries the wearer as
 *       the attacking entity.</li>
 *   <li><b>Double illager loot</b>: the same kills drop every item twice. Vanilla already
 *       applied Looting while building the loot, so the doubling sits on top of it and the
 *       two stack naturally.</li>
 *   <li><b>Emeralds from hostile mobs</b>: any {@link Enemy} killed by the wearer has a
 *       small chance to drop an extra emerald. Independent of Looting and of the doubling
 *       above (an illager rolls it once, like every other monster).</li>
 * </ol>
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class EmeraldHeroAbility {

    /**
     * Passive: the buff lasts 30 s and is re-applied every second, the same numbers the
     * miner ring uses - long enough that the icon never blinks, short enough that the
     * blessing fades within half a minute of losing the ring.
     */
    private static final int BUFF_DURATION_TICKS = 600;
    private static final int BUFF_REFRESH_TICKS = 20;
    /** Damage multiplier the wearer deals to illagers. */
    private static final float RAIDER_DAMAGE_MULTIPLIER = 2.0F;
    /** Chance for every hostile mob the wearer kills to leave one emerald behind. */
    private static final float EMERALD_DROP_CHANCE = 0.1F;

    /** Players currently carrying the hero buff, so losing the ring clears it once. */
    private static final Set<UUID> BUFFED = new HashSet<>();

    // ------------------------------------------------------------------
    // Passive: Hero of the Village
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack ring = wornRing(player);
        if (ring.isEmpty()) {
            // Ring taken off (or drained): drop the buff once instead of waiting it out.
            if (BUFFED.remove(player.getUUID())) {
                player.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
            }
            return;
        }
        if (player.level().getGameTime() % BUFF_REFRESH_TICKS != 0) {
            return;                          // once per second is plenty for a 30 s buff
        }
        // The blessing is exactly as strong as the raid the ring was carried through, so a
        // ring charged in a level III raid gives Hero of the Village III (amplifier 2).
        applyBuff(player, HeroLevel.of(ring) - 1);
        BUFFED.add(player.getUUID());
    }

    /**
     * Keeps the blessing at the wanted level (the ring's raid level). A stronger instance
     * that is already on the player is removed first, otherwise swapping to a ring with a
     * lower level would keep the old strength until it expires - the same guard the miner
     * ring uses for its depth scaled Haste.
     */
    private static void applyBuff(ServerPlayer player, int amplifier) {
        MobEffectInstance current = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        if (current != null && current.getAmplifier() > amplifier) {
            player.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
        }
        // ambient (no particles), showIcon: the player should see the blessing is on.
        player.addEffect(new MobEffectInstance(
                MobEffects.HERO_OF_THE_VILLAGE, BUFF_DURATION_TICKS, amplifier, true, false, true));
    }

    // ------------------------------------------------------------------
    // Passive: double damage against illagers
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !isRaider(victim)) {
            return;
        }
        // getEntity() is the entity responsible for the hit, so a wearer's arrow counts too.
        if (!(event.getSource().getEntity() instanceof Player player) || wornRing(player).isEmpty()) {
            return;
        }
        event.setAmount(event.getAmount() * RAIDER_DAMAGE_MULTIPLIER);
    }

    // ------------------------------------------------------------------
    // Passive: double illager loot + a small emerald chance on every monster
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof Player player) || wornRing(player).isEmpty()) {
            return;
        }
        if (isRaider(victim)) {
            doubleDrops(victim.level(), event.getDrops());
        }
        if (victim instanceof Enemy && mobLootEnabled(victim)) {
            RandomSource random = victim.getRandom();
            if (random.nextFloat() < EMERALD_DROP_CHANCE) {
                // Added to the event's own collection: vanilla drops exactly this list
                // afterwards, so no separate spawn is needed.
                event.getDrops().add(new ItemEntity(victim.level(),
                        victim.getX(), victim.getY(), victim.getZ(), new ItemStack(Items.EMERALD)));
            }
        }
    }

    /**
     * Doubles every drop. A stack that would overflow its maximum size is split instead of
     * capped, so single items (a crossbow, a banner, ...) really do drop twice.
     */
    private static void doubleDrops(Level level, Collection<ItemEntity> drops) {
        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem();
            int doubled = stack.getCount() * 2;
            int max = stack.getMaxStackSize();
            if (doubled <= max) {
                stack.setCount(doubled);
                continue;
            }
            stack.setCount(max);
            for (int rest = doubled - max; rest > 0; rest -= max) {
                ItemEntity overflow = new ItemEntity(level, drop.getX(), drop.getY(), drop.getZ(),
                        stack.copyWithCount(Math.min(rest, max)));
                overflow.setDefaultPickUpDelay();
                extra.add(overflow);
            }
        }
        drops.addAll(extra);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** True for the illager family (the vanilla {@code minecraft:raiders} entity tag). */
    private static boolean isRaider(LivingEntity entity) {
        // wrapAsHolder is the non-deprecated way in; builtInRegistryHolder() is @Deprecated.
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).is(EntityTypeTags.RAIDERS);
    }

    /**
     * The bonus emerald is a mob drop, so it follows the {@code doMobLoot} game rule like
     * every vanilla drop: with mob loot switched off it is not given out at all.
     */
    private static boolean mobLootEnabled(LivingEntity victim) {
        return victim.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
    }

    /** The worn emerald ring, or an empty stack: needs the ability and durability left. */
    private static ItemStack wornRing(Player player) {
        if (!CuriosCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        if (ring.getItem() instanceof FlightRingItem item
                && item.hasAbility(RingAbility.EMERALD_HERO)
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
        BUFFED.remove(playerId);
    }

    private EmeraldHeroAbility() {
    }
}
