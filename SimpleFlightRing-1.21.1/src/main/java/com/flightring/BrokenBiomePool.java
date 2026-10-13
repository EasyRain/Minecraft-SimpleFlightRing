package com.flightring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * The candidate biome pool the broken dimension's islands are picked from (M2 step 1).
 * <p>
 * This is deliberately a separate class because two very different consumers need the same list:
 * the biome source's {@code collectPossibleBiomes()} (which the vanilla pipeline uses to budget
 * features and structures - a biome missing from it never runs its features) and the island layout,
 * which asks "which biome is the island at (gridX, gridZ)?".
 * <p>
 * Filters, in order (the reasoning is in the project docs, PLAN-破碎维度.md 3.7.1 / 3.7.9):
 * <ol>
 *   <li>the void biome is never an island biome - it is what the islands float in;</li>
 *   <li>mod biomes can be switched off entirely (default: on);</li>
 *   <li>whole namespaces can be excluded - functional mods (compact machines &amp; friends) ship an
 *       empty dimension plus a custom biome that generates <b>nothing</b>, and an island of "nothing"
 *       would just be a bare rock;</li>
 *   <li>single biomes can be excluded by id;</li>
 *   <li><b>pure void is the only kind of biome that is always dropped</b> (the void the islands float
 *       in, plus mod biomes whose id says void/empty). Nether and end biomes are <b>welcome</b>: a
 *       floating island of warped forest or end highlands is exactly the kind of surprise this
 *       dimension is for (user decision 2026-10-09);</li>
 *   <li>only biomes tagged overworld / nether / end participate ({@link #requireDimensionTag}, added
 *       2026-10-11 after a foreign-dimension biome crashed the feature sorter with a feature order
 *       cycle);</li>
 *   <li>the "generates nothing" net ({@link #excludeFeaturelessBiomes}) stays on: it is what keeps the
 *       empty and functional biomes out (the user's own reference cases are
 *       {@code allthemodium:the_beyond} and {@code useless_mod:useless_biome});</li>
 *   <li><b>pure void is the only kind of biome that is always dropped</b> (the void the islands float
 *       in, plus mod biomes whose id says void/empty). Nether and end biomes are <b>welcome</b>: a
 *       floating island of warped forest or end highlands is exactly the kind of surprise this
 *       dimension is for (user decision 2026-10-09).</li>
 * </ol>
 * Ocean and river biomes are <b>kept</b>: they become "bowl" islands that hold their water (see the
 * docs), so the vanilla ocean/river biomes are all usable.
 * <p>
 * The dump at server start (gated by {@link #logPool}) is how the lists above get tuned: it prints
 * the pool, which biomes are liquid (bowl) and every rejection with its reason, so mod biome ids can
 * be copied straight out of the log instead of guessed.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class BrokenBiomePool {

    // ------------------------------------------------------------------ settings
    // These mirror the agreed defaults; wiring them to a config file is the next small step
    // (see PLAN-破碎维度.md 3.7.4 for the key list).

    /** Whether biomes from other namespaces take part (user decision 2026-10-09: on by default). */
    public static boolean allowModBiomes = true;

    /**
     * Drop biomes that carry no placed feature at all (the "generates nothing" net).
     * <p>
     * <b>Off since 2026-10-11 (second time, now for the right reason).</b> This net looked sensible but
     * the criterion is wrong: a mod's dimension biome can have complete terrain and a full biome setup
     * while its feature list is empty on the registry side - {@code allthemodium:the_other} is exactly
     * that, and the net threw the whole {@code allthemodium} namespace out (user 2026-10-11: "atm 全给
     * 排出去了，一个不剩，实际上 atm 里 the_other 维度是有完整的群系和地形生成的"). Empty and functional
     * biomes are therefore named explicitly in {@link #BIOME_BLACKLIST} instead, and the general safety
     * net is the feature order check ({@link #createsOrderCycle}) plus the void filter.
     */
    public static boolean excludeFeaturelessBiomes = false;

    /** Whole namespaces to skip; compact machines is the case the user reported. */
    public static List<String> NAMESPACE_BLACKLIST = List.of("compactmachines");

    /**
     * Id keywords of biomes that exist for machinery rather than for a landscape. Space mods are the case
     * the user reported (ATM10, 2026-10-11): {@code ad_astra:orbit} and the planet orbit biomes in general
     * are empty "functional" biomes - nothing generates in them, so an island of one is a bare rock. The
     * "looks functional" report does not catch them because it also requires the absence of mob spawns,
     * which these biomes do define. Add keywords here as more mods turn up.
     */
    public static List<String> FUNCTIONAL_KEYWORDS = List.of("orbit");

    /**
     * Single biomes to skip (highest priority). These are the biomes a human identified as empty or
     * purely functional - the examples come from the user's own pack:
     * <ul>
     *   <li>{@code useless_mod:useless_biome} - a deliberately empty test biome;</li>
     *   <li>{@code allthemodium:the_beyond} - ATM's decorative "beyond" biome, nothing generates in it
     *       (unlike {@code allthemodium:the_other}, which is a real dimension biome and stays in);</li>
     *   <li>{@code ae2:spatial_storage} - AE2's spatial storage cell dimension.</li>
     * </ul>
     */
    public static List<ResourceLocation> BIOME_BLACKLIST = List.of(
            ResourceLocation.fromNamespaceAndPath("useless_mod", "useless_biome"),
            ResourceLocation.fromNamespaceAndPath("allthemodium", "the_beyond"),
            ResourceLocation.fromNamespaceAndPath("ae2", "spatial_storage"));

    /** Set to true to dump the pool and every rejection to the log once (becomes a config key). */
    public static boolean logPool = true;

    /**
     * Drop biomes that look purely functional: <b>no placed feature, no mob spawn and none of the three
     * dimension tags</b> - the shape an "empty machine dimension" biome has (AE2's spatial storage,
     * compact machines, useless_mod's test biome).
     * <p>
     * <b>Off by default, and reported in the log instead.</b> Two previous attempts at guessing this
     * criterion were wrong and threw away biomes the user wanted (the whole {@code allthemodium}
     * namespace, then again with the dimension-tag rule), so the rule is published as
     * {@code [BIOMES] maybe empty/functional = [...]} first and only switched on once the user confirms
     * the list matches their expectation. Named cases go in {@link #BIOME_BLACKLIST} meanwhile.
     */
    public static boolean excludeFunctionalBiomes = false;

    /** Biomes the user wants kept even if a filter would drop them. */
    public static List<ResourceLocation> ALLOWED_BIOMES = List.of();

    /**
     * The biome ids this world was generated with, set by {@link BrokenWorldSettings} from the world's own saved
     * settings. When non-empty the pool is rebuilt as exactly this list, so that {@code pool.get(biomeIndex %
     * size)} keeps pointing at the same biome and a config edit cannot reshuffle a world that already exists.
     */
    public static List<String> frozenBiomeIds = List.of();

    // ------------------------------------------------------------------ state

    private static volatile List<Holder<Biome>> cached;
    private static final List<String> REJECTED = new ArrayList<>();

    /** Biomes kept in the pool although their own feature list had to be emptied (order conflict). */
    private static final List<String> STRIPPED = new ArrayList<>();

    /** Biomes kept after their own features were reordered to the pool's canonical order. */
    private static final List<String> REORDERED_BIOMES = new ArrayList<>();

    /** Reordered settings per biome id, for the biomes that needed canonicalising. */
    private static final java.util.Map<ResourceLocation, BiomeGenerationSettings> REORDERED =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Ids of the biomes whose features are dropped for this dimension. */
    private static final java.util.Set<ResourceLocation> FEATURE_STRIPPED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private BrokenBiomePool() {
    }

    /**
     * Rebuilds the pool once the registries are final.
     * <p>
     * This hook is the important one, and it exists because of a hard crash: the biome source is built
     * while the dimension JSON is parsed, which is <b>before</b> NeoForge applies its biome modifiers -
     * a modded biome's feature list is empty at that point, so the incremental feature order check below
     * saw nothing to disagree with, and the real sorter later threw
     * {@code Feature order cycle found, involved sources: [minecraft:deep_dark, allthemodium:warped_forest]}
     * on the chunk source thread (the client just hangs on "not responding"). Rebuilding here - after
     * datapack registry loading, before any level exists - means the check runs against the same feature
     * lists the generator will really use.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        // Pick up a hand-edited config file: this runs once per server start, and only after mod loading are
        // the config values safe to read (see BrokenConfig#onCommonSetup for why not from ModConfigEvent).
        // NOTE: the world's frozen settings cannot be read here - MinecraftServer#overworld() is still null at
        // this point (crashed 2026-10-13); that happens in onServerStarted below.
        BrokenConfig.snapshot();
        net.minecraft.core.RegistryAccess access = event.getServer().registryAccess();
        long t0 = System.nanoTime();
        // Dimensions first: the pool build wants to know which biomes belong to a dimension that
        // generates no terrain at all (see generatesNoTerrain).
        mapDimensions(access);
        long t1 = System.nanoTime();
        rebuild(access.lookupOrThrow(Registries.BIOME));
        long t2 = System.nanoTime();
        // The startup cost this mod adds, split into its two parts, so "is it worth caching?" is a
        // question about numbers instead of a guess.
        FlightRingMod.LOGGER.info("[BIOMES] startup cost: dimension mapping = {} ms, pool build = {} ms (total {} ms)",
                (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t2 - t0) / 1_000_000);
    }

    /** biome id -> the noise settings of the dimension that contains that biome. */
    private static final java.util.Map<ResourceLocation, ResourceLocation> DIMENSION_SETTINGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** biome id -> whether its dimension generates no terrain at all (its settings' default block is air). */
    private static final java.util.Map<ResourceLocation, Boolean> TERRAINLESS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Works out which noise settings each biome's dimension uses, by asking every dimension in the level
     * stem registry which biomes its generator can produce:
     * {@code dimension -> generator -> biome source -> possibleBiomes()}.
     * <p>
     * This is what lets an island of a mod biome borrow <b>that mod's own rock and surface rule</b>. The
     * problem it solves (user 2026-10-11): allthemodium ships exactly two noise settings
     * ({@code the_beyond}, {@code the_other}) for eight biomes, so matching by the biome's own id only
     * worked for one of them and the other seven fell back to the overworld - stone, dirt and grass, on
     * which their features (which expect crimson nylium, soul sand and the like) cannot place anything.
     * The settings are looked up as: the dimension's own id, then the biome's own id, then the only entry
     * of the biome's namespace.
     */
    private static void mapDimensions(net.minecraft.core.RegistryAccess access) {
        DIMENSION_SETTINGS.clear();
        TERRAINLESS.clear();
        java.util.List<ResourceLocation> known = new ArrayList<>();
        java.util.Map<ResourceLocation, net.minecraft.world.level.levelgen.NoiseGeneratorSettings> byId =
                new java.util.HashMap<>();
        access.lookupOrThrow(Registries.NOISE_SETTINGS).listElements().forEach(ref -> {
            known.add(ref.key().location());
            byId.put(ref.key().location(), ref.value());
        });
        access.lookupOrThrow(Registries.LEVEL_STEM).listElements().forEach(stem -> {
            ResourceLocation dimensionId = stem.key().location();
            for (Holder<Biome> biome : stem.value().generator().getBiomeSource().possibleBiomes()) {
                if (biome.unwrapKey().isEmpty()) {
                    continue;
                }
                ResourceLocation biomeId = biome.unwrapKey().get().location();
                ResourceLocation settings = null;
                if (known.contains(dimensionId)) {
                    settings = dimensionId;
                } else {
                    ResourceLocation sameAsBiome =
                            ResourceLocation.fromNamespaceAndPath(dimensionId.getNamespace(), biomeId.getPath());
                    if (known.contains(sameAsBiome)) {
                        settings = sameAsBiome;
                    } else {
                        ResourceLocation only = null;
                        for (ResourceLocation candidate : known) {
                            if (!candidate.getNamespace().equals(biomeId.getNamespace())) {
                                continue;
                            }
                            if (only != null) {
                                only = null;
                                break;
                            }
                            only = candidate;
                        }
                        settings = only;
                    }
                }
                if (settings != null) {
                    DIMENSION_SETTINGS.putIfAbsent(biomeId, settings);
                    net.minecraft.world.level.levelgen.NoiseGeneratorSettings resolved = byId.get(settings);
                    if (resolved != null) {
                        // "Does this dimension put anything there at all?" - a functional dimension (a space
                        // mod's orbit, a storage cell) has air as its default block, a real one has rock.
                        TERRAINLESS.putIfAbsent(biomeId, resolved.defaultBlock().isAir());
                    }
                }
            }
        });
        FlightRingMod.LOGGER.info("[BIOMES] biome -> dimension noise settings = {}", DIMENSION_SETTINGS);
    }

    /** The noise settings an island of this biome should borrow, or {@code null} when unknown. */
    public static ResourceLocation noiseSettingsFor(Holder<Biome> biome) {
        ResourceLocation id = id(biome);
        return id == null ? null : DIMENSION_SETTINGS.get(id);
    }

    /** Forces a fresh build from the given registry, even if one was already cached from earlier. */
    public static synchronized List<Holder<Biome>> rebuild(HolderLookup.RegistryLookup<Biome> biomes) {
        registry = biomes;
        cached = build(biomes);
        return cached;
    }

    /** Dumps the pool once when the server is up, if {@link #logPool} is on. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Always fill the pool and publish the seed here: the biome source and the island layout are
        // consulted from the first generated chunk on, and they read these two static values (see
        // BrokenIslandSeed for why the seed has to travel this way).
        BrokenIslandSeed.set(event.getServer().overworld().getSeed());
        ServerLevel level = event.getServer().getLevel(BrokenPortalTravel.BROKEN_DIMENSION);
        ServerLevel source = level != null ? level : event.getServer().overworld();
        // A world that already has settings keeps them: islands that exist (or are half generated) then keep the
        // shape and the biomes they were made with, and config edits only affect worlds created later. This has
        // to run here rather than in onServerAboutToStart, where the overworld - and with it the data storage -
        // does not exist yet.
        BrokenWorldSettings settings = BrokenWorldSettings.get(event.getServer());
        if (settings.isFrozen()) {
            settings.apply();
            cached = null;
            registry = null;
        } else {
            frozenBiomeIds = List.of();
        }
        pool(source);
        if (!settings.isFrozen()) {
            settings.capture();
        }
        BrokenIslandLayout.debugLog(BrokenIslandSeed.get());
        if (logPool) {
            log(source);
        }
    }

    /** The biome registry the pool was built from, kept so the biome source can resolve the void biome. */
    private static HolderLookup.RegistryLookup<Biome> registry;

    /** The pool as the biome source sees it; empty until the pool has been built. */
    public static List<Holder<Biome>> cachedPool() {
        List<Holder<Biome>> current = cached;
        return current == null ? List.of() : current;
    }

    /** The biome registry captured at server start, or {@code null} before that. */
    public static synchronized HolderLookup.RegistryLookup<Biome> registry() {
        return registry;
    }

    /** The biome pool of this server, built once and cached (the registries never change at runtime). */
    public static synchronized List<Holder<Biome>> pool(ServerLevel level) {
        return poolFrom(level.registryAccess().lookupOrThrow(Registries.BIOME));
    }

    /** Builds (once per registry) and returns the pool from a biome registry. */
    public static synchronized List<Holder<Biome>> poolFrom(HolderLookup.RegistryLookup<Biome> biomes) {
        // Rebuild whenever a *different* registry instance shows up. A single player session builds new
        // registries every time a world is entered, and holders from the previous registry are stale:
        // feeding them into the chunk serialiser is what produced
        // "No value with id -1" on the client (LinearPalette.read) and the null holder NPE inside
        // Registry$1.getId on the server. Caching by identity keeps the pool cheap within one world and
        // correct across world loads.
        if (cached == null || registry != biomes) {
            registry = biomes;
            cached = build(biomes);
        }
        return cached;
    }

    /** True for biomes whose island has to be a water-holding "bowl". */
    public static boolean isLiquid(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_OCEAN)
                || biome.is(BiomeTags.IS_DEEP_OCEAN)
                || biome.is(BiomeTags.IS_RIVER);
    }

    /**
     * True when this biome would decorate nothing at all.
     * <p>
     * <b>Mob spawning is deliberately not part of this.</b> The user's point (2026-10-11): some functional
     * biomes allow spawning on purpose - players build automatic mob farms in them - so spawns say nothing
     * about whether a biome is a landscape. What a functional dimension (a space mod's orbit, a storage
     * cell) actually lacks is <b>terrain</b>, which is what {@link #generatesNoTerrain(Holder)} tests.
     */
    public static boolean isFeatureless(Holder<Biome> biome) {
        for (HolderSet<PlacedFeature> step : biome.value().getGenerationSettings().features()) {
            if (step.size() > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when this biome's dimension generates no terrain where the biome is.
     * <p>
     * The most practical criterion for a "functional biome", and the one the user asked for: instead of
     * guessing from mob spawns (functional biomes often allow spawning so players can farm), look at what
     * the dimension actually puts down - a space mod's orbit or a storage dimension has <b>air</b> as its
     * default block, a real dimension has rock. This is read from the noise settings the dimension uses,
     * which {@link #mapDimensions(net.minecraft.core.RegistryAccess)} has already resolved.
     */
    public static boolean generatesNoTerrain(Holder<Biome> biome) {
        ResourceLocation id = id(biome);
        return id != null && Boolean.TRUE.equals(TERRAINLESS.get(id));
    }

    /**
     * A rough "nothing happens here" note for the log: no placed feature and no overworld/nether/end tag.
     * Spawns are ignored on purpose (see {@link #isFeatureless(Holder)}).
     */
    public static boolean looksFunctional(Holder<Biome> biome) {
        return isFeatureless(biome)
                && !biome.is(BiomeTags.IS_OVERWORLD)
                && !biome.is(BiomeTags.IS_NETHER)
                && !biome.is(BiomeTags.IS_END);
    }

    /** Everything the last build dropped, with the reason - for the log and for debugging. */
    public static synchronized List<String> rejected() {
        return List.copyOf(REJECTED);
    }

    // ------------------------------------------------------------------ internals

    private static List<Holder<Biome>> build(HolderLookup.RegistryLookup<Biome> biomes) {
        REJECTED.clear();
        STRIPPED.clear();
        REORDERED_BIOMES.clear();
        REORDERED.clear();
        FEATURE_STRIPPED.clear();
        // Vanilla biomes are accepted first on purpose: when a mod biome disagrees with the global
        // feature order, the mod biome is the one that must lose. Iterating in registry order once
        // dropped minecraft:deep_dark instead (user log 2026-10-11).
        List<Holder.Reference<Biome>> candidates = new ArrayList<>(biomes.listElements().toList());
        if (!frozenBiomeIds.isEmpty()) {
            // A world that already exists keeps the exact list it was generated with, so pool.get(biomeIndex %
            // size) keeps pointing at the same biome. An entry whose mod has been removed since keeps its slot by
            // repeating the previous surviving entry: without that the list would get shorter and the biome of
            // every later island would shift in newly generated chunks (user 2026-10-14). The canonical sort just
            // below only depends on the set, so a stable set keeps a stable order.
            Map<String, Holder.Reference<Biome>> byId = new LinkedHashMap<>();
            for (Holder.Reference<Biome> ref : candidates) {
                byId.put(String.valueOf(id(ref)), ref);
            }
            List<Holder.Reference<Biome>> restored = new ArrayList<>(frozenBiomeIds.size());
            Holder.Reference<Biome> previous = null;
            int missing = 0;
            for (String frozenId : frozenBiomeIds) {
                Holder.Reference<Biome> ref = byId.get(frozenId);
                if (ref == null) {
                    missing++;
                    ref = previous;
                } else {
                    previous = ref;
                }
                if (ref != null) {
                    restored.add(ref);
                }
            }
            if (!restored.isEmpty()) {
                candidates = restored;
                FlightRingMod.LOGGER.info(
                        "[BIOMES] pool restored from the world's frozen settings: {} slots, {} missing"
                                + " (kept as placeholders)",
                        restored.size(), missing);
            }
        }
        candidates.sort(java.util.Comparator.comparingInt(b -> "minecraft".equals(id(b).getNamespace()) ? 0 : 1));
        // 1) cheap filters first - no sorter involved: void, blacklists, "dimension generates no terrain",
        //    id keywords. Everything that survives is a candidate.
        List<Holder<Biome>> kept = new ArrayList<>();
        List<Holder<Biome>> passFilters = new ArrayList<>();
        for (Holder.Reference<Biome> biome : candidates) {
            String reason = rejectionReason(biome);
            if (reason != null) {
                REJECTED.add(id(biome) + " (" + reason + ")");
            } else {
                passFilters.add(biome);
            }
        }
        long orderCheckNanos = 0L;

        // 2) The order the *vanilla* biomes already use is the pool's canonical order. Deriving it from
        //    minecraft:* only costs a single sorter run, and those biomes are mutually consistent by
        //    construction.
        List<Holder<Biome>> vanillaCore = new ArrayList<>();
        for (Holder<Biome> biome : passFilters) {
            if ("minecraft".equals(id(biome).getNamespace())) {
                vanillaCore.add(biome);
            }
        }
        List<List<PlacedFeature>> canonical = null;
        try {
            canonical = canonicalOrder(vanillaCore, biomes);
        } catch (IllegalStateException e) {
            canonical = null;
        }

        // 3) Put every non-vanilla biome into that order up front: same features, same steps, only the
        //    sequence inside a step changes. That is what stops a mod's ordering from ever contradicting
        //    the pool - and doing it for all of them costs no sorter run at all.
        //    This replaced "try the sorter once per candidate", which with 310 biomes took ~12 seconds of
        //    server startup (measured on the user's ATM10 server, 2026-10-11).
        if (canonical != null) {
            for (Holder<Biome> biome : passFilters) {
                if (!"minecraft".equals(id(biome).getNamespace()) && !isFeatureless(biome)) {
                    REORDERED.put(id(biome), canonicalise(biome, canonical));
                    REORDERED_BIOMES.add(String.valueOf(id(biome)));
                }
            }
        }

        // 4) Verify the whole set once. Only when that still disagrees (a mod that contradicts itself, or
        //    a vanilla core that is somehow not consistent) fall back to the slow incremental algorithm.
        long verifyStart = System.nanoTime();
        boolean consistent = canonical != null && orderIsConsistent(biomes, passFilters);
        orderCheckNanos += System.nanoTime() - verifyStart;
        if (consistent) {
            kept.addAll(passFilters);
        } else {
            REORDERED.clear();
            REORDERED_BIOMES.clear();
            FEATURE_STRIPPED.clear();
            STRIPPED.clear();
            kept.clear();
            for (Holder<Biome> biome : passFilters) {
                // The vanilla sorter merges every participating biome's feature list into one global order
                // and throws "Feature order cycle found" when two of them disagree; that exception is
                // thrown while a chunk is generated, which kills the chunk source thread and leaves the
                // client hanging. So a candidate only joins when the order still works.
                long orderStart = System.nanoTime();
                boolean conflicts = createsOrderCycle(biomes, kept, biome);
                orderCheckNanos += System.nanoTime() - orderStart;
                if (conflicts) {
                    // Keep the mod's features: reorder them into the pool's order and try again. Only if
                    // even that disagrees fall back to emptying the list, and only then drop the biome.
                    ResourceLocation id = id(biome);
                    REORDERED.put(id, canonicalise(biome, canonicalOrder(kept, biomes)));
                    if (createsOrderCycle(biomes, kept, biome)) {
                        REORDERED.remove(id);
                        FEATURE_STRIPPED.add(id);
                        if (createsOrderCycle(biomes, kept, biome)) {
                            FEATURE_STRIPPED.remove(id);
                            REJECTED.add(id + " (feature order cycle with the biomes already in the pool)");
                            continue;
                        }
                        STRIPPED.add(String.valueOf(id));
                    } else {
                        REORDERED_BIOMES.add(String.valueOf(id));
                    }
                }
                kept.add(biome);
            }
        }

        FlightRingMod.LOGGER.info("[BIOMES] kept but reordered to fit the pool's feature order = {} biomes ({})",
                REORDERED_BIOMES.size(), REORDERED_BIOMES.size() > 6
                        ? String.join(", ", REORDERED_BIOMES.subList(0, 6)) + ", …" : REORDERED_BIOMES);
        FlightRingMod.LOGGER.info("[BIOMES] kept with their own features dropped (unfixable order conflict) = {}", STRIPPED);
        FlightRingMod.LOGGER.info("[BIOMES] order check cost = {} ms for {} biomes (one canonical run + one verification)",
                orderCheckNanos / 1_000_000, candidates.size());
        return List.copyOf(kept);
    }

    /** True when the whole set can be sorted into one feature order (i.e. the pool is usable as it is). */
    private static boolean orderIsConsistent(HolderLookup.RegistryLookup<Biome> biomes, List<Holder<Biome>> pool) {
        List<Holder<Biome>> trial = new ArrayList<>(pool);
        Holder<Biome> voidBiome = biomes.getOrThrow(Biomes.THE_VOID);
        if (!trial.contains(voidBiome)) {
            trial.add(voidBiome);
        }
        try {
            FeatureSorter.buildFeaturesPerStep(trial,
                    biome -> BrokenIslandChunkGenerator.islandSettings(biome).features(), true);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** True when this biome is in the pool but its own features had to be dropped. */
    public static boolean isFeatureStripped(Holder<Biome> biome) {
        ResourceLocation id = id(biome);
        return id != null && FEATURE_STRIPPED.contains(id);
    }

    /** The reordered settings of a biome, or {@code null} when its own settings can be used as they are. */
    public static BiomeGenerationSettings reordered(Holder<Biome> biome) {
        ResourceLocation id = id(biome);
        return id == null ? null : REORDERED.get(id);
    }

    /** The feature order the accepted pool already uses, per generation step. */
    private static List<List<PlacedFeature>> canonicalOrder(List<Holder<Biome>> accepted,
                                                            HolderLookup.RegistryLookup<Biome> biomes) {
        List<Holder<Biome>> trial = new ArrayList<>(accepted);
        trial.add(biomes.getOrThrow(Biomes.THE_VOID));
        List<List<PlacedFeature>> order = new ArrayList<>();
        for (FeatureSorter.StepFeatureData step : FeatureSorter.buildFeaturesPerStep(trial,
                biome -> BrokenIslandChunkGenerator.islandSettings(biome).features(), true)) {
            order.add(step.features());
        }
        return order;
    }

    /**
     * The biome's own generation settings with each step's features put into the pool's canonical order.
     * The set of features is untouched - this only reorders them, which is all the vanilla sorter needs
     * to stop seeing a contradiction.
     */
    private static BiomeGenerationSettings canonicalise(Holder<Biome> biome, List<List<PlacedFeature>> canonical) {
        BiomeGenerationSettings original = biome.value().getGenerationSettings();
        BiomeGenerationSettings.PlainBuilder builder = new BiomeGenerationSettings.PlainBuilder();
        List<HolderSet<PlacedFeature>> steps = original.features();
        for (int step = 0; step < steps.size(); step++) {
            List<Holder<PlacedFeature>> features = new ArrayList<>();
            steps.get(step).forEach(features::add);
            int currentStep = step;
            features.sort(java.util.Comparator
                    .comparingInt((Holder<PlacedFeature> feature) -> positionIn(canonical, currentStep, feature))
                    .thenComparing(feature -> feature.unwrapKey()
                            .map(key -> key.location().toString()).orElse("")));
            for (Holder<PlacedFeature> feature : features) {
                builder.addFeature(step, feature);
            }
        }
        return builder.build();
    }

    /** Where this feature sits in the pool's canonical order for that step; unknown features go last. */
    private static int positionIn(List<List<PlacedFeature>> canonical, int step, Holder<PlacedFeature> feature) {
        if (step >= canonical.size()) {
            return Integer.MAX_VALUE;
        }
        int index = canonical.get(step).indexOf(feature.value());
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    /**
     * True when adding this candidate would make the global feature order inconsistent. The trial list is
     * the pool plus the void biome, i.e. exactly what {@code collectPossibleBiomes()} hands the vanilla
     * sorter, so a pool that passes here cannot surprise the generator later.
     */
    private static boolean createsOrderCycle(HolderLookup.RegistryLookup<Biome> biomes,
                                            List<Holder<Biome>> accepted, Holder<Biome> candidate) {
        List<Holder<Biome>> trial = new ArrayList<>(accepted);
        trial.add(candidate);
        Holder<Biome> voidBiome = biomes.getOrThrow(Biomes.THE_VOID);
        if (!trial.contains(voidBiome)) {
            trial.add(voidBiome);
        }
        try {
            FeatureSorter.buildFeaturesPerStep(trial,
                    biome -> BrokenIslandChunkGenerator.islandSettings(biome).features(), true);
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** Why this biome is not usable as an island, or {@code null} when it is. */
    private static String rejectionReason(Holder<Biome> biome) {
        ResourceLocation id = id(biome);
        if (biome.is(Biomes.THE_VOID)) {
            return "is the void the islands float in";
        }
        boolean vanilla = "minecraft".equals(id.getNamespace());
        // An explicit allowlist entry is a human decision, so it wins over every list and every heuristic
        // below - that is the "fine-grained fallback" the user asked for.
        if (ALLOWED_BIOMES.contains(id)) {
            return null;
        }
        // The lists are about MODDED biomes only: vanilla biomes are always in (user 2026-10-11: "黑白名单
        // 需要排除原版群系，也就是说原版群系必定生成"). The only vanilla biome that never becomes an island is
        // minecraft:the_void, and that is the hard rule above, not a list.
        if (!vanilla) {
            if (!allowModBiomes) {
                return "mod biomes are disabled";
            }
            if (NAMESPACE_BLACKLIST.contains(id.getNamespace())) {
                return "namespace (= mod id) blacklisted";
            }
            if (BIOME_BLACKLIST.contains(id)) {
                return "biome blacklisted";
            }
        }
        if (isVoidLike(id, biome) && featureCount(biome) == 0) {
            return "is a void biome (id) and lists no feature";
        }
        if (generatesNoTerrain(biome)) {
            return "its dimension generates no terrain (default block is air)";
        }
        for (String keyword : FUNCTIONAL_KEYWORDS) {
            if (!matchesKeyword(id, keyword)) {
                continue;
            }
            int features = featureCount(biome);
            // Always report the match, so a keyword that does not fire can be seen and tuned instead of
            // silently doing nothing.
            FlightRingMod.LOGGER.info("[BIOMES]   ~ {} matches functional keyword \"{}\" with {} features -> {}",
                    id, keyword, features, features == 0 ? "dropped (empty)" : "kept (it has content)");
            if (features == 0) {
                return "is a functional biome (id matches \"" + keyword + "\" and it lists no feature)";
            }
        }
        if (excludeFunctionalBiomes && looksFunctional(biome)) {
            return "looks purely functional (no features, no spawns, no dimension tag)";
        }
        // Only mod biomes are subject to the "generates nothing" net (off by default, see the field).
        if (excludeFeaturelessBiomes && !"minecraft".equals(id.getNamespace()) && isFeatureless(biome)) {
            return "mod biome that generates no placed feature at all";
        }
        return null;
    }

    /** True for the void the islands float in and for mod biomes that are pure void as well. */
    private static boolean isVoidLike(ResourceLocation id, Holder<Biome> biome) {
        if (biome.is(Biomes.THE_VOID)) {
            return true;
        }
        String path = id.getPath();
        return path.contains("void") || path.contains("empty");
    }

    /**
     * True when the id is exactly the keyword or ends with {@code _<keyword>}.
     * <p>
     * Deliberately narrower than a plain "contains": the user's objection to keyword filters was exactly
     * this (2026-10-11) - a mod we have never seen could ship a real landscape biome with the keyword
     * somewhere inside its name. Together with the "lists no feature" gate in
     * {@link #rejectionReason(Holder)} a plain substring match would have been an unnecessary risk.
     */
    private static boolean matchesKeyword(ResourceLocation id, String keyword) {
        String path = id.getPath();
        return path.equals(keyword) || path.endsWith("_" + keyword);
    }

    private static ResourceLocation id(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.location()).orElse(null);
    }

    /** One-shot dump of the pool and the rejections; see {@link #logPool}. */
    public static synchronized void log(ServerLevel level) {
        List<Holder<Biome>> pool = pool(level);
        Map<String, Integer> byNamespace = new LinkedHashMap<>();
        List<String> liquid = new ArrayList<>();
        for (Holder<Biome> biome : pool) {
            ResourceLocation id = id(biome);
            if (id != null) {
                byNamespace.merge(id.getNamespace(), 1, Integer::sum);
            }
            if (isLiquid(biome)) {
                liquid.add(String.valueOf(id));
            }
        }
        FlightRingMod.LOGGER.info("[BIOMES] pool = {} biomes, by namespace = {}", pool.size(), byNamespace);
        FlightRingMod.LOGGER.info("[BIOMES] bowl (liquid) biomes = {}", liquid);
        FlightRingMod.LOGGER.info("[BIOMES] rejected = {} (showing up to 40)", REJECTED.size());
        REJECTED.stream().limit(40).forEach(entry -> FlightRingMod.LOGGER.info("[BIOMES]   - {}", entry));

        // Verdict for every "empty/functional" candidate, so a real run answers "does this still
        // generate?" without any guessing: the known cases (keywords, blacklists, dimensions without
        // terrain) get one line each, saying whether the biome ended up in the pool.
        java.util.List<String> watch = new ArrayList<>();
        level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().forEach(ref -> {
            ResourceLocation id = ref.key().location();
            boolean keyword = FUNCTIONAL_KEYWORDS.stream().anyMatch(k -> matchesKeyword(id, k));
            boolean black = BIOME_BLACKLIST.contains(id) || NAMESPACE_BLACKLIST.contains(id.getNamespace());
            boolean terrainless = generatesNoTerrain(ref);
            if (keyword || black || terrainless) {
                watch.add(id + " -> " + (pool.contains(ref) ? "IN POOL (would generate)" : "excluded")
                        + "  [keyword=" + keyword + " blacklist=" + black + " terrainless=" + terrainless
                        + " features=" + featureCount(ref) + "]");
            }
        });
        FlightRingMod.LOGGER.info("[BIOMES] verdict for empty/functional candidates ({}):", watch.size());
        watch.forEach(line -> FlightRingMod.LOGGER.info("[BIOMES]   ? {}", line));
        // Per-biome facts for the mod biomes, so the filters above can be tuned from data instead of
        // guesses: feature steps, features, mob entities and the three dimension tags.
        List<String> functional = new ArrayList<>();
        pool.stream()
                .filter(biome -> {
                    ResourceLocation id = id(biome);
                    return id != null && !"minecraft".equals(id.getNamespace());
                })
                .limit(30)
                .forEach(biome -> {
                    boolean functionalLooking = looksFunctional(biome);
                    if (functionalLooking) {
                        functional.add(String.valueOf(id(biome)));
                    }
                    FlightRingMod.LOGGER.info(
                            "[BIOMES]   + {} steps={} features={} entities={} temp={} over={} nether={} end={}{}",
                            id(biome), populatedSteps(biome), featureCount(biome),
                            biome.value().getMobSettings().getEntityTypes().size(),
                            biome.value().getBaseTemperature(),
                            biome.is(BiomeTags.IS_OVERWORLD), biome.is(BiomeTags.IS_NETHER), biome.is(BiomeTags.IS_END),
                            functionalLooking ? "   <-- looks empty/functional" : "");
                });
        FlightRingMod.LOGGER.info("[BIOMES] maybe empty/functional (no features and no dimension tag; spawns ignored on purpose) = {}", functional);

        // Which noise settings exist per namespace: a mod biome without a dimension tag borrows its rock
        // and surface rule from a settings entry of its own namespace (same id first), so this is the list
        // that decides whether that worked.
        java.util.Map<String, List<String>> settingsByNamespace = new java.util.LinkedHashMap<>();
        for (Holder.Reference<net.minecraft.world.level.levelgen.NoiseGeneratorSettings> ref
                : level.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).listElements().toList()) {
            ResourceLocation id = ref.key().location();
            settingsByNamespace.computeIfAbsent(id.getNamespace(), key -> new ArrayList<>()).add(id.getPath());
        }
        FlightRingMod.LOGGER.info("[BIOMES] noise settings by namespace = {}", settingsByNamespace);
    }

    /** How many generation steps actually carry features. */
    private static int populatedSteps(Holder<Biome> biome) {
        int steps = 0;
        for (HolderSet<PlacedFeature> step : biome.value().getGenerationSettings().features()) {
            if (step.size() > 0) {
                steps++;
            }
        }
        return steps;
    }

    /** Total number of placed features the biome lists. */
    private static int featureCount(Holder<Biome> biome) {
        int count = 0;
        for (HolderSet<PlacedFeature> step : biome.value().getGenerationSettings().features()) {
            count += step.size();
        }
        return count;
    }
}
