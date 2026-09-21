package com.flightring;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.EnderManAngerEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Passives of the ender relic ring, "Warp Nexus". Like every other relic ability they only work
 * while the ring is worn in the Curios flight ring slot and still has durability.
 * <p>
 * The four kinds of creature the ring is built around - endermen, endermites, shulkers and phantoms
 * - treat the wearer as one of their own and never pick them as a target (they still hit back if
 * the wearer starts it, the same rule the ocean and raid rings follow). Endermen are the awkward
 * one, because they are already neutral: what makes them hostile is a player's stare, which is
 * refused for a wearer through NeoForge's own {@code EnderManAngerEvent} - the very hook that
 * carved pumpkins use, so nothing about the mechanic is reimplemented here.
 * <p>
 * On top of that the wearer never attracts phantoms (vanilla asks
 * {@link PlayerSpawnPhantomsEvent} before every insomnia spawn, so a wearer simply answers no) and
 * levitation cannot land on them at all - an instance that is already there is wiped every tick the
 * way the infernal ring wipes withering.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class WarpNexusPassives {

    /** The mobs that count the wearer as one of their own (see the entity type tag). */
    private static final TagKey<EntityType<?>> NEUTRALS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "warp_nexus_neutrals"));

    /** The monsters this player has attacked: those are allowed to fight back. */
    private static final Map<UUID, Set<UUID>> PROVOKED = new HashMap<>();

    /** An enderman's stare never angers a wearer. */
    @SubscribeEvent
    public static void onEnderManAnger(EnderManAngerEvent event) {
        if (!wornRing(event.getPlayer()).isEmpty()) {
            event.setCanceled(true);
        }
    }

    /** Levitation never lands on a wearer. */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!event.getEffectInstance().getEffect().is(MobEffects.LEVITATION)) {
            return;
        }
        if (event.getEntity() instanceof Player player && !wornRing(player).isEmpty()) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /** ...and one that is already on them (a command, another mod) is wiped. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || wornRing(player).isEmpty()) {
            return;
        }
        if (player.hasEffect(MobEffects.LEVITATION)) {
            player.removeEffect(MobEffects.LEVITATION);
        }
    }

    /** No insomnia for a wearer: vanilla asks this before it spawns a single phantom. */
    @SubscribeEvent
    public static void onSpawnPhantoms(PlayerSpawnPhantomsEvent event) {
        if (!wornRing(event.getEntity()).isEmpty()) {
            event.setResult(PlayerSpawnPhantomsEvent.Result.DENY);
        }
    }

    /** The four kinds see the wearer as kin, so they never take them as a target. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!isNeutral(event.getEntity())) {
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

    /** Attacking one of them is what makes it hit back: that is remembered here. */
    @SubscribeEvent
    public static void onNeutralHurt(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!isNeutral(victim)) {
            return;
        }
        if (event.getSource().getEntity() instanceof Player player && !wornRing(player).isEmpty()) {
            PROVOKED.computeIfAbsent(player.getUUID(), key -> new HashSet<>()).add(victim.getUUID());
        }
    }

    private static boolean isNeutral(Entity entity) {
        // wrapAsHolder is the non-deprecated way in: EntityType#is(TagKey) was removed in 26.1.2
        // and is deprecated in 1.21.1, builtInRegistryHolder() is deprecated in both.
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).is(NEUTRALS);
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

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PROVOKED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        PROVOKED.remove(event.getOriginal().getUUID());
    }

    /** The worn ender ring, or an empty stack: needs the ability and durability left. */
    private static ItemStack wornRing(Player player) {
        if (!CuriosCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        if (ring.getItem() instanceof FlightRingItem item
                && item.hasAbility(RingAbility.WARP_NEXUS)
                && item.isUsable(ring)) {
            return ring;
        }
        return ItemStack.EMPTY;
    }

    private WarpNexusPassives() {
    }
}
