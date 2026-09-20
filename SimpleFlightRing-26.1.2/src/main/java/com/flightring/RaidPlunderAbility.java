package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.OminousBottleAmplifier;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * "Great Plunder!" - the ability of the raid relic ring. Passive, and like every other relic
 * ability it only works while the ring is worn in the Curios slot and still has durability left.
 * <ol>
 *   <li><b>The illager host takes the wearer for one of its own</b>: raiders may not target the
 *       wearer - but a raider the wearer attacked is remembered and keeps fighting back until it
 *       dies or the wearer leaves its follow range (the same rule the sculk and ocean rings use
 *       for their own monsters).</li>
 *   <li><b>Bad Omen cannot take hold</b>: the effect is refused while the ring is worn and any
 *       leftover is cleared once when the ring goes on. The raid omen of an ominous bottle is
 *       deliberately left alone - the wearer still needs it to call a raid on a village.</li>
 *   <li><b>The wearer trades with the host</b>: thirty-two emeralds buy an ominous bottle (level I..V)
 *       from a raid captain, thirty-two buy a totem of undying from an evoker that has not been
 *       attacked.</li>
 *   <li><b>Villagers know an illager</b>: they refuse to trade and flee from the wearer (see
 *       {@code VillagerFearMixin}), and slaying one yields a hoard of emeralds plus the goods of
 *       their trade.</li>
 *   <li><b>Iron golems are the wearer's enemies</b>: every golem within
 *       {@value #GOLEM_RAGE_RADIUS} blocks is sent after the wearer and its persistent anger is
 *       refreshed, so it keeps hunting for another 20-39 s after the ring comes off and the
 *       grudge only ends when it dies or the wearer leaves its range.</li>
 *   <li><b>The ring is a totem of undying</b>: a fatal hit spends one {@link RaidCharges charge}
 *       and revives the wearer exactly as the vanilla totem would, particles and all.</li>
 * </ol>
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class RaidPlunderAbility {

    // ------------------------------------------------------------------
    // Balance
    // ------------------------------------------------------------------

    /** Emeralds one ominous bottle costs at a raid captain. */
    private static final int BOTTLE_PRICE = 32;
    /** Emeralds one totem of undying costs at an evoker. */
    private static final int TOTEM_PRICE = 64;
    /** An ominous bottle is worth 0..4, i.e. raid omen level I..V - the same as a vanilla drop. */
    private static final int BOTTLE_MAX_AMPLIFIER = 4;
    /** Iron golems this close to the wearer are sent after them. */
    private static final double GOLEM_RAGE_RADIUS = 24.0;
    /** The golem sweep runs once a second; the anger timer itself is refreshed every 10 s. */
    private static final int GOLEM_SWEEP_INTERVAL_TICKS = 20;
    private static final int GOLEM_ANGER_REFRESH_TICKS = 200;
    /** Emeralds a slain villager leaves behind, added on top of the profession's own goods. */
    private static final int VILLAGER_EMERALD_MIN = 10;
    private static final int VILLAGER_EMERALD_MAX = 20;
    /** Enchantment power the librarian's book is rolled with - the vanilla level-30 roll. */
    private static final int LIBRARIAN_BOOK_LEVEL = 30;

    /** What a slain villager drops besides the emeralds; a missing entry means emeralds only. */
    private static final Map<String, Item> PROFESSION_LOOT = professionLootTable();

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    /** The raiders each wearer has provoked, per player (see {@code OceanFavoredAbility}). */
    private static final Map<UUID, Set<UUID>> PROVOKED = new HashMap<>();
    /** Wearers whose leftover Bad Omen has already been cleared for the current wearing session. */
    private static final Set<UUID> BAD_OMEN_CLEARED = new HashSet<>();

    // ------------------------------------------------------------------
    // 1. Raiders are neutral (but still hit back)
    // ------------------------------------------------------------------

    /** Remembers WHICH raider the wearer attacked, so that one may retaliate. */
    @SubscribeEvent
    public static void onRaiderHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Raider raider)) {
            return;
        }
        if (event.getSource().getEntity() instanceof Player player && isWearer(player)) {
            PROVOKED.computeIfAbsent(player.getUUID(), key -> new HashSet<>()).add(raider.getUUID());
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Raider raider)) {
            return;
        }
        if (!(event.getNewAboutToBeSetTarget() instanceof Player player) || !isWearer(player)) {
            return;
        }
        if (isProvokedBy(player, raider)) {
            return; // the wearer picked that fight: let the raider answer
        }
        // Clearing the target (instead of cancelling) also drops one the raider grabbed earlier.
        event.setNewAboutToBeSetTarget(null);
    }

    /** True while this very raider is hunting the wearer (attacked and still in range). */
    static boolean isProvokedBy(Player player, Entity raider) {
        Set<UUID> provoked = PROVOKED.get(player.getUUID());
        if (provoked == null || !provoked.contains(raider.getUUID())) {
            return false;
        }
        if (!raider.isAlive() || raider.level() != player.level()
                || raider.distanceToSqr(player) > Math.pow(followRange(raider), 2.0)) {
            provoked.remove(raider.getUUID());   // out of range (or gone): no more hatred
            return false;
        }
        return true;
    }

    /** True when this evoker has been attacked by the wearer, so it refuses to trade. */
    static boolean isProvokedEvoker(Player player, Entity evoker) {
        return isProvokedBy(player, evoker);
    }

    private static double followRange(Entity mob) {
        return mob instanceof LivingEntity living
                ? living.getAttributeValue(Attributes.FOLLOW_RANGE)
                : 16.0;
    }

    // ------------------------------------------------------------------
    // 2. Bad Omen immunity
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player) || wornRing(player).isEmpty()) {
            return;
        }
        if (!event.getEffectInstance().getEffect().is(MobEffects.BAD_OMEN)) {
            return;
        }
        // The ominous bottle's raid omen is deliberately NOT refused: the wearer still has to be
        // able to call a raid on a village.
        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    // ------------------------------------------------------------------
    // 3. Trades with the host, 4. villagers refuse to trade
    // ------------------------------------------------------------------

    /**
     * The wearer's interactions with the village and with the host. Runs before
     * {@code Villager#mobInteract} in both game versions, so cancelling the event really does
     * stop the trade screen from opening.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isWearer(player)) {
            return;
        }
        Entity target = event.getTarget();
        if (target instanceof Villager) {
            // Rule 4: villagers refuse to trade with the wearer (and flee, see VillagerFearMixin).
            refuse(event);
            if (isTradingHand(event)) {
                actionBar(player, "message.simpleflightring.raid_villager_refuses");
            }
            return;
        }
        if (target instanceof Raider raider && raider.isCaptain()) {
            // Raid captains are marked by Raider#isCaptain, there is no "raid_captain" entity tag.
            refuse(event);
            if (isTradingHand(event)) {
                tradeOminousBottle(player, raider);
            }
            return;
        }
        if (target.getType() == EntityType.EVOKER) {
            // Compared by EntityType: the evoker lives in a different package in 26.1.2.
            refuse(event);
            if (isTradingHand(event)) {
                tradeTotem(player, target);
            }
        }
    }

    /**
     * Cancels the vanilla interaction and reports it as a success. The result matters: left at the
     * default pass the client keeps trying and immediately uses the other hand, so a single right
     * click would settle the trade twice - once per hand.
     */
    private static void refuse(PlayerInteractEvent.EntityInteract event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
    }

    /** True for the hand that settles a trade, so one click pays once. */
    private static boolean isTradingHand(PlayerInteractEvent.EntityInteract event) {
        return event.getHand() == InteractionHand.MAIN_HAND;
    }

    /** Thirty-two emeralds buy an ominous bottle of a random level (I..V). */
    private static void tradeOminousBottle(ServerPlayer player, Raider captain) {
        if (!payEmeralds(player, BOTTLE_PRICE)) {
            return;
        }
        RandomSource random = captain.getRandom();
        int amplifier = random.nextInt(BOTTLE_MAX_AMPLIFIER + 1);
        ItemStack bottle = new ItemStack(Items.OMINOUS_BOTTLE);
        // 26.1.2 wraps the amplifier in its own component record instead of a raw Integer.
        bottle.set(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, new OminousBottleAmplifier(amplifier));
        give(player, bottle);
        actionBar(player, "message.simpleflightring.raid_bottle_trade", HeroLevel.roman(amplifier + 1));
    }

    /** Sixty-four emeralds buy a totem of undying - unless the wearer attacked that evoker. */
    private static void tradeTotem(ServerPlayer player, Entity evoker) {
        if (isProvokedEvoker(player, evoker)) {
            actionBar(player, "message.simpleflightring.raid_evoker_refuses");
            return;
        }
        if (!payEmeralds(player, TOTEM_PRICE)) {
            return;
        }
        give(player, new ItemStack(Items.TOTEM_OF_UNDYING));
        actionBar(player, "message.simpleflightring.raid_totem_trade");
    }

    // ------------------------------------------------------------------
    // 5. Iron golems turn on the wearer
    // ------------------------------------------------------------------

    /**
     * Sends every iron golem within {@value #GOLEM_RAGE_RADIUS} blocks after the wearer and
     * refreshes its persistent anger, so the golem's own anger goal keeps re-acquiring the
     * wearer for another {@code TimeUtil.rangeOfSeconds(20, 39)} after the ring comes off - that
     * is the "break the grudge" behaviour. Vanilla's {@code IronGolem#canAttack} refuses
     * player-created golems and creative players, so a golem the wearer built themselves may
     * well ignore them.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % GOLEM_SWEEP_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ItemStack ring = wornRing(player);
            if (ring.isEmpty()) {
                continue;
            }
            if (player.level().getGameTime() % GOLEM_ANGER_REFRESH_TICKS == 0) {
                angerGolems(player);
            } else {
                retargetGolems(player);
            }
        }
    }

    /** Once every 10 s: hand out the persistent anger, so it outlives the ring. */
    private static void angerGolems(ServerPlayer player) {
        for (IronGolem golem : nearbyGolems(player)) {
            golem.setTarget(player);
            // 26.1.2 stores the anger target as an EntityReference instead of a bare UUID.
            golem.setPersistentAngerTarget(EntityReference.of(player));
            golem.startPersistentAngerTimer();
        }
    }

    /** Every second in between: keep the golems actually chasing the wearer. */
    private static void retargetGolems(ServerPlayer player) {
        for (IronGolem golem : nearbyGolems(player)) {
            if (golem.getTarget() != player && golem.canAttack(player)) {
                golem.setTarget(player);
            }
        }
    }

    private static List<IronGolem> nearbyGolems(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(GOLEM_RAGE_RADIUS);
        return player.level().getEntitiesOfClass(IronGolem.class, box);
    }

    // ------------------------------------------------------------------
    // 6. Slaying a villager: a hoard of emeralds and the goods of their trade
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !isWearer(player)) {
            return;
        }
        RandomSource random = villager.getRandom();
        int emeralds = VILLAGER_EMERALD_MIN
                + random.nextInt(VILLAGER_EMERALD_MAX - VILLAGER_EMERALD_MIN + 1);
        addDrop(event.getDrops(), new ItemStack(Items.EMERALD, emeralds), villager);
        ItemStack goods = professionLootFor(villager, random);
        if (!goods.isEmpty()) {
            addDrop(event.getDrops(), goods, villager);
        }
    }

    private static void addDrop(Collection<ItemEntity> drops, ItemStack stack, LivingEntity at) {
        ItemEntity drop = new ItemEntity(at.level(), at.getX(), at.getY(), at.getZ(), stack);
        drop.setDefaultPickUpDelay();
        drops.add(drop);
    }

    /** The profession material of a slain villager, rolled for the librarian's enchanted book. */
    private static ItemStack professionLootFor(Villager villager, RandomSource random) {
        // 26.1.2 holds the profession as a Holder<VillagerProfession>, so unwrap its key.
        Optional<ResourceKey<VillagerProfession>> profession = villager.getVillagerData()
                .profession().unwrapKey();
        if (profession.isEmpty()) {
            return ItemStack.EMPTY;              // modded profession without a key: emeralds only
        }
        if ("librarian".equals(profession.get().identifier().getPath())) {
            return randomEnchantedBook(villager, random);
        }
        Item item = PROFESSION_LOOT.get(profession.get().identifier().getPath());
        return item == null ? ItemStack.EMPTY : new ItemStack(item);   // nitwit/none/unknown
    }

    /** The librarian's own stock: a book with one random enchantment at a random level. */
    private static ItemStack randomEnchantedBook(Villager villager, RandomSource random) {
        ItemStack book = new ItemStack(Items.BOOK);
        Stream<Holder<Enchantment>> source = villager.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .listElements()
                .map(holder -> (Holder<Enchantment>) holder);
        return EnchantmentHelper.enchantItem(random, book, LIBRARIAN_BOOK_LEVEL, source);
    }

    /** The profession -> material table; anything the wearer cannot claim drops emeralds only. */
    private static Map<String, Item> professionLootTable() {
        Map<String, Item> loot = new HashMap<>();
        loot.put("farmer", Items.WHEAT);
        loot.put("fisherman", Items.COD);
        loot.put("shepherd", Items.WHITE_WOOL);
        loot.put("fletcher", Items.FLINT);
        loot.put("cartographer", Items.PAPER);
        loot.put("cleric", Items.GLOWSTONE_DUST);
        loot.put("armorer", Items.IRON_INGOT);
        loot.put("weaponsmith", Items.IRON_INGOT);
        loot.put("toolsmith", Items.FLINT);
        loot.put("butcher", Items.COOKED_BEEF);
        loot.put("leatherworker", Items.LEATHER);
        loot.put("mason", Items.STONE_BRICKS);
        // librarian is handled separately (a random enchanted book) and nitwit/none/unknown
        // professions deliberately drop nothing but the emeralds.
        return Map.copyOf(loot);
    }

    // ------------------------------------------------------------------
    // 7. The ring as a totem of undying
    // ------------------------------------------------------------------

    /**
     * A hit that would kill the wearer spends one charge and revives them exactly the way the
     * vanilla totem of undying does - effects cured, Regeneration, Absorption, Fire Resistance,
     * and the totem's own entity event (byte 35) for the particles, the sound and the item
     * animation. Only a hit that really would have killed counts, and void damage or
     * {@code /kill} stay lethal, exactly like the vanilla totem.
     */
    @SubscribeEvent
    public static void onFatalDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        float damage = event.getNewDamage();
        if (damage <= 0.0F) {
            return;
        }
        if (damage < player.getHealth() + player.getAbsorptionAmount()) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        ItemStack ring = wornRing(player);
        int charges = RaidCharges.of(ring);
        if (ring.isEmpty() || charges <= 0) {
            return;
        }

        event.setNewDamage(0.0F);
        RaidCharges.set(ring, charges - 1);
        revive(player);
        actionBar(player, "message.simpleflightring.raid_totem_used",
                Integer.toString(RaidCharges.of(ring)));
        FlightRingMod.LOGGER.debug("[FlightRing] {} was saved by the raid ring ({} charges left)",
                player.getName().getString(), RaidCharges.of(ring));
    }

    /** The Totem of Undying's rescue, shared with {@code BurstTotemHandler#revive}'s pattern. */
    private static void revive(ServerPlayer player) {
        player.setHealth(1.0F);
        // NeoForge's EffectCures helper is not available on 26.1.2, so the harmful effects are
        // dropped explicitly - the same loop BurstTotemHandler uses.
        for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
            if (!effect.getEffect().value().isBeneficial()) {
                player.removeEffect(effect.getEffect());
            }
        }
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        // Byte 35 is the vanilla totem's entity event: particles, sound and the held item
        // animation, for free and identical in both game versions.
        player.level().broadcastEntityEvent(player, (byte) 35);
    }

    // ------------------------------------------------------------------
    // Passives on the player: Bad Omen cleanup
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        if (wornRing(player).isEmpty()) {
            BAD_OMEN_CLEARED.remove(id);    // next time the ring goes on, clear again
            return;
        }
        if (!BAD_OMEN_CLEARED.add(id)) {
            return;                          // already handled for this wearing session
        }
        if (player.hasEffect(MobEffects.BAD_OMEN)) {
            player.removeEffect(MobEffects.BAD_OMEN);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** The worn raid ring, or an empty stack: needs the ability and durability left. */
    static ItemStack wornRing(Player player) {
        if (!CuriosCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        if (ring.getItem() instanceof FlightRingItem item
                && item.hasAbility(RingAbility.RAID_PLUNDER)
                && item.isUsable(ring)) {
            return ring;
        }
        return ItemStack.EMPTY;
    }

    /**
     * True while this player wears a usable raid ring, {@code false} for a null player.
     * <p>
     * Public because {@code VillagerFearMixin} decides villager fear on both sides with it (the
     * Curios slot is readable on the client for the local player, so no packet is needed).
     */
    public static boolean isWearer(Player player) {
        return player != null && !wornRing(player).isEmpty();
    }

    /** True while the wearer carries at least {@code price} emeralds; says so when not. */
    private static boolean payEmeralds(ServerPlayer player, int price) {
        if (countEmeralds(player) < price) {
            actionBar(player, "message.simpleflightring.raid_no_emeralds", Integer.toString(price));
            return false;
        }
        takeEmeralds(player, price);
        return true;
    }

    private static int countEmeralds(ServerPlayer player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(Items.EMERALD)) {
                total += stack.getCount();
            }
        }
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (offhand.is(Items.EMERALD)) {
            total += offhand.getCount();
        }
        return total;
    }

    /** Takes the emeralds out of the hands and then the inventory; the count is already known. */
    private static void takeEmeralds(ServerPlayer player, int amount) {
        int left = amount;
        left = takeFrom(player.getInventory().getSelectedItem(), left);
        if (left > 0) {
            left = takeFrom(player.getItemBySlot(EquipmentSlot.OFFHAND), left);
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (left <= 0) {
                break;
            }
            left = takeFrom(stack, left);
        }
    }

    private static int takeFrom(ItemStack stack, int amount) {
        if (amount <= 0 || !stack.is(Items.EMERALD)) {
            return amount;
        }
        int taken = Math.min(amount, stack.getCount());
        stack.shrink(taken);
        return amount - taken;
    }

    /** Hands the trade over, and drops it at the wearer's feet when there is no room. */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            // 26.1.2's drop() carries the "thrown from hand" flag as a third argument.
            player.drop(stack, false, false);
        }
    }

    private static void actionBar(ServerPlayer player, String key, Object... args) {
        // 26.1.2 sends action bar lines with ServerPlayer#sendOverlayMessage.
        player.sendOverlayMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY));
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
        PROVOKED.remove(playerId);
        BAD_OMEN_CLEARED.remove(playerId);
        RaidFangAbility.clear(playerId);
        FlameLordAbility.clear(playerId);
    }

    private RaidPlunderAbility() {
    }
}
