package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The ability key of the infernal relic ring: <b>"Flame Lord"</b>. Every living creature within
 * {@value #BASE_RADIUS} blocks of the wearer is lit with {@link EternalSoulFireEffect} - soul fire
 * that never goes out and that only the sea's blessing or a Fire Resistance potion can wear down -
 * and every dropped item that a furnace could have processed comes out of the blast already
 * smelted.
 * <p>
 * <b>Energy Burst</b> ({@link ModEnchantments#ENERGY_BURST}) widens the blast by the same factor it
 * raises the ring's ability damage with: {@code 5.0 * abilityDamageMultiplier(ring)}, so a level V
 * enchant reaches 11.25 blocks instead of 5. The burn itself uses that factor too - it is recorded
 * on every victim by {@link EternalSoulFireEffect#apply} - while the level of the effect is always
 * III (a 1-based level of 3, i.e. amplifier 2) and adds no damage of its own.
 * <p>
 * The wearer is never caught in their own blast, and neither is any creature wearing a working
 * ocean relic ring, whose blessing shrugs the mark off.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class FlameLordAbility {

    /** Cooldown of the Flame Lord's wrath, as the task specifies. */
    private static final int COOLDOWN_TICKS = 300;
    /** The blast starts at five blocks around the wearer and grows with Energy Burst. */
    private static final double BASE_RADIUS = 5.0;
    /** Level of the eternal soul fire the key applies: level III, i.e. amplifier 2. */
    private static final int EFFECT_LEVEL = 3;
    /**
     * Recipe types a caught drop is offered to, in this order. The first one that matches wins, so
     * a raw food that both a furnace and a smoker would take comes out of the furnace's recipe -
     * the same order a player would think of "smelting".
     */
    private static final List<RecipeType<?>> SMELTING_TYPES =
            List.of(RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING);

    /** How long the ring of soul fire takes to sweep out from the wearer, in ticks. */
    private static final int RING_ANIMATION_TICKS = 12;
    /** The sweep starts as a small circle on the floor under the wearer. */
    private static final double RING_START_RADIUS = 0.7;

    /** Sweeps in flight, so a cast can be seen spreading out to the radius it really has. */
    private static final List<Ring> RINGS = new ArrayList<>();

    /** One cast's sweep: where it started, how far it reaches and when it began. */
    private record Ring(ServerLevel level, double x, double y, double z, double radius, long start) {
    }
    /** The ability key's internal cooldown, per player. */
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    /**
     * Sets everything around the worn infernal ring alight, if the ability is off cooldown. Called
     * by {@link RingAbilityKeyHandler} when the ability key is pressed.
     */
    static void tryCast(ServerPlayer player, ItemStack ring) {
        // 26.1.2 renamed ServerPlayer#serverLevel to a covariant ServerPlayer#level().
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Long readyAt = READY_AT.get(player.getUUID());
        if (readyAt != null && now < readyAt) {
            long seconds = Math.max(1L, (readyAt - now + 19L) / 20L);
            // 26.1.2 sends action bar lines with ServerPlayer#sendOverlayMessage.
            player.sendOverlayMessage(Component.translatable(
                    "message.simpleflightring.flame_lord_cooldown", seconds).withStyle(ChatFormatting.GRAY));
            return;
        }

        float multiplier = RingAbilities.abilityDamageMultiplier(ring);
        double radius = BASE_RADIUS * multiplier;
        int marked = burnCreatures(player, level, radius, multiplier);
        int smelted = smeltDrops(level, player.position(), radius);

        READY_AT.put(player.getUUID(), now + COOLDOWN_TICKS);
        // Let the wearer see the blast: a small soul fire circle that sweeps out to the real radius.
        RINGS.add(new Ring(level, player.getX(), player.getY(), player.getZ(), radius, now));
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS,
                1.0F, 1.0F);
        FlightRingMod.LOGGER.debug(
                "[FlightRing] {} cast Flame Lord: {} creatures lit, {} drops smelted (radius {})",
                player.getName().getString(), marked, smelted, radius);
    }

    /** Advances every sweep in flight; a sweep is twelve ticks of expanding soul fire. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (RINGS.isEmpty()) {
            return;
        }
        RINGS.removeIf(ring -> {
            long elapsed = ring.level().getGameTime() - ring.start();
            if (elapsed > RING_ANIMATION_TICKS) {
                return true;
            }
            drawRing(ring, elapsed);
            return false;
        });
    }

    /**
     * One frame of a sweep: a small circle under the wearer that spins for the first quarter of
     * the animation and then rushes outwards to the blast radius, so the reach of the ability is
     * visible to everyone standing in it.
     */
    private static void drawRing(Ring ring, long elapsed) {
        double progress = Math.min(1.0, elapsed / (double) RING_ANIMATION_TICKS);
        double grown = progress < 0.25 ? 0.0 : (progress - 0.25) / 0.75;
        double currentRadius = RING_START_RADIUS + (ring.radius() - RING_START_RADIUS) * grown;
        int points = Math.max(14, (int) Math.min(64.0, currentRadius * 6.0));
        double spin = elapsed * 0.35;
        for (int i = 0; i < points; i++) {
            double angle = spin + (Math.PI * 2.0 * i) / points;
            double x = ring.x() + Math.cos(angle) * currentRadius;
            double z = ring.z() + Math.sin(angle) * currentRadius;
            ring.level().sendParticles(ModParticles.SOUL_FLAME.get(), x, ring.y() + 0.15, z,
                    1, 0.0, 0.0, 0.0, 0.0);

        }
    }
    /**
     * Lights every living creature in range, except the caster and every wearer of a working ocean
     * ring.
     *
     * @return how many creatures were actually marked
     */
    private static int burnCreatures(ServerPlayer player, ServerLevel level, double radius,
                                     float multiplier) {
        AABB box = player.getBoundingBox().inflate(radius);
        int marked = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                candidate -> candidate != player && candidate.isAlive())) {
            if (EternalSoulFireEffect.isOceanRingWearer(target)) {
                continue;                       // the sea's own cannot be burned
            }
            EternalSoulFireEffect.apply(target, EFFECT_LEVEL, multiplier);
            marked++;
        }
        return marked;
    }

    /**
     * Turns every smeltable drop in range into what a furnace would have made of it, keeping the
     * whole stack: the output is the recipe's result count times the input count, capped at the
     * output item's maximum stack size (so 64 raw beef becomes 64 steak, not 64 * 1 spread out).
     * Drops no recipe accepts are left exactly as they are.
     *
     * @return how many stacks were smelted
     */
    private static int smeltDrops(ServerLevel level, Vec3 center, double radius) {
        AABB box = new AABB(center, center).inflate(radius);
        // getEntitiesOfClass returns a live list, so the drops are collected first: every converted
        // entity is replaced while the list is being walked.
        List<ItemEntity> drops = new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class, box,
                drop -> !drop.getItem().isEmpty()));
        int smelted = 0;
        for (ItemEntity drop : drops) {
            ItemStack result = smelt(level, drop.getItem());
            if (result.isEmpty()) {
                continue;
            }
            replaceDrop(level, drop, result);
            smelted++;
        }
        return smelted;
    }

    /**
     * The furnace product of one drop: the first of {@link #SMELTING_TYPES} that accepts the item
     * wins and its result comes back with the count the whole input stack would yield.
     * {@link ItemStack#EMPTY} when nothing smelts it.
     */
    private static ItemStack smelt(ServerLevel level, ItemStack input) {
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        for (RecipeType<?> type : SMELTING_TYPES) {
            ItemStack result = recipeResult(level, type, recipeInput, input);
            if (!result.isEmpty()) {
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * The scaled output of the first recipe of {@code type} matching the input, or empty.
     * <p>
     * The loop over {@link #SMELTING_TYPES} cannot name a concrete recipe class (smelting, blasting
     * and smoking are three different ones), so the lookup is done with the raw type. That is safe
     * here because the recipe type's own generic parameter is checked at runtime: a blasting recipe
     * is never returned for {@code RecipeType.SMELTING}. The result itself is read from
     * {@code SingleItemRecipe#assemble}, which 26.1.2 takes without a registry access argument.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ItemStack recipeResult(ServerLevel level, RecipeType<?> type,
                                          SingleRecipeInput recipeInput, ItemStack input) {
        // 26.1.2 replaced Level#getRecipeManager with ServerLevel#recipeAccess.
        Optional<RecipeHolder<?>> found = ((RecipeManager) level.recipeAccess())
                .getRecipeFor((RecipeType) type, recipeInput, level);
        if (found.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack product = ((Recipe<SingleRecipeInput>) found.get().value()).assemble(recipeInput);
        if (product.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int total = product.getCount() * input.getCount();
        ItemStack result = product.copy();
        result.setCount(Math.min(total, result.getMaxStackSize()));
        return result;
    }

    /** Throws the smelted stack out where the raw drop was and takes the raw one away. */
    private static void replaceDrop(ServerLevel level, ItemEntity drop, ItemStack result) {
        ItemEntity converted = new ItemEntity(level, drop.getX(), drop.getY(), drop.getZ(), result);
        converted.setDeltaMovement(0.0, 0.0, 0.0);
        level.addFreshEntity(converted);
        level.sendParticles(ParticleTypes.SMOKE, drop.getX(), drop.getY() + 0.2, drop.getZ(),
                6, 0.2, 0.2, 0.2, 0.01);
        drop.discard();
    }

    /**
     * Fired when the wearer leaves, so a returning player starts with a clean cooldown. Called
     * from {@link RaidPlunderAbility}, which already listens for the logout and clone events.
     */
    static void clear(UUID playerId) {
        READY_AT.remove(playerId);
    }

    private FlameLordAbility() {
    }
}
