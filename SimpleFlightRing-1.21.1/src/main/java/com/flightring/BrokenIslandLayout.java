package com.flightring;

import java.util.Random;

/**
 * Where the broken dimension's islands are (M2 step 2): a pure, stateless function of the world seed
 * and the grid cell, shared by the biome source and the chunk generator.
 * <p>
 * Everything here is deliberately <b>deterministic</b>: the same seed always produces the same
 * islands, in the same places, with the same size, height and biome (user requirement 2026-10-09:
 * "只要种子相同就必定能遇到"). Nothing is stored and no runtime randomness is used, because
 * {@code fillFromNoise} and {@code getNoiseBiome} are called per chunk and in any order - each call
 * has to be able to answer "is there an island here, and what does it look like?" on its own.
 * <p>
 * Layout: a square grid of cells, one island per cell, the centre jittered by at most half the
 * minimum gap. That makes the minimum distance between two islands a geometric certainty
 * ({@code cell = 2 * maxRadius + minGap}, jitter {@code <= minGap / 2}) instead of something that has
 * to be checked at runtime.
 * <p>
 * The biome is not chosen here - it comes from {@link BrokenBiomePool}, which needs the server
 * registries - so this class only hands out a stable index, which the consumer maps into the pool.
 */
public final class BrokenIslandLayout {

    // ------------------------------------------------------------------ settings
    // Values agreed with the user; these become config keys in the next step (PLAN-破碎维度.md 3.7.4).

    /** Island radius is random in this closed range (80..320 on user request 2026-10-11, to hold big structures). */
    public static int MIN_RADIUS = 80;
    public static int MAX_RADIUS = 320;

    /**
     * Minimum horizontal distance between two island edges, random in this range (user 2026-10-09:
     * 128 turned out too tight once the island's own radius is added to the centre distance).
     * <p>
     * The lattice spacing is the middle of the range and the jitter half of its spread, so two
     * axis-aligned neighbours land anywhere in {@code [GAP_MIN, GAP_MAX]} (the exact value also
     * depends on how big the two radii happen to be, and diagonal neighbours are naturally farther -
     * {@link #GAP_MIN} is the hard floor, which is what the debug dump checks).
     */
    public static int GAP_MIN = 256;
    public static int GAP_MAX = 512;

    /** Candidate heights an island centre is picked from. */
    /**
     * The y levels islands are centred on. Raised one step (2026-10-11) because the islands are now 3-4x
     * thicker, and a 200 block thick body centred on y=64 would just be a column standing on the world
     * floor.
     */
    public static int[] HEIGHT_LAYERS = {96, 112, 128};

    /** How much longer than wide an island may come out (1.0 = a round disc). */
    public static double MAX_STRETCH = 2.2;

    /** How much a centre may move inside its cell: half the spread of the gap range. */
    public static int MAX_JITTER = (GAP_MAX - GAP_MIN) / 4;

    private BrokenIslandLayout() {
    }

    /**
     * Edge length of one grid cell.
     * <p>
     * The worst case is two jittered neighbours moving towards each other, so the cell has to cover
     * both diameters (the radii eat into the gap too!), the middle of the gap range and both jitters:
     * {@code 2 * maxRadius + (GAP_MIN + GAP_MAX) / 2}. Getting this wrong shows up immediately in the
     * debug dump as a too small edge-to-edge gap (the first attempt used {@code 2 * maxRadius +
     * gap} with a fixed gap of 128 and measured 63 instead of the intended floor).
     */
    public static int cellSize() {
        return 2 * MAX_RADIUS + (GAP_MIN + GAP_MAX) / 2;
    }

    /**
     * The island of one grid cell. {@code biomeIndex} is a stable non-negative number; the caller maps
     * it into its biome pool ({@code biomeIndex % poolSize}).
     * <p>
     * {@code radius} is the <b>major</b> semi-axis and {@code stretch} the ratio between the axes, so
     * an island can come out long instead of just wide (user 2026-10-09). The ellipse stays inscribed
     * in the radius circle, which is why the gap guarantee above still holds unchanged.
     */
    public record Island(int gridX, int gridZ, int centerX, int centerZ, int radius, int centerY,
                         int biomeIndex, int variant, double stretch, double angle) {

        /** Elliptical distance: 0 at the centre, 1 on the rim. */
        public double normalizedDistance(int blockX, int blockZ) {
            double dx = blockX - centerX;
            double dz = blockZ - centerZ;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double alongMajor = dx * cos + dz * sin;
            double alongMinor = -dx * sin + dz * cos;
            double a = alongMajor / radius;
            double b = alongMinor / (radius / stretch);
            return Math.sqrt(a * a + b * b);
        }

        /** True when the given block column is inside this island. */
        public boolean contains(int blockX, int blockZ) {
            return normalizedDistance(blockX, blockZ) <= 1.0;
        }

        /**
         * True when a whole axis-aligned box is inside this island. The island is convex (an ellipse), so
         * testing the four corners is enough. Used to decide whether an island can hold a structure (user:
         * "有些结构特别大，比如古代城…至少得接得住").
         */
        public boolean containsBox(int minX, int minZ, int maxX, int maxZ) {
            return contains(minX, minZ) && contains(minX, maxZ)
                    && contains(maxX, minZ) && contains(maxX, maxZ);
        }
    }

    /** The island of a grid cell; the same arguments always give the same island. */
    public static Island islandAt(long seed, int gridX, int gridZ) {
        Random random = new Random(mix(seed, gridX, gridZ));
        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        int cell = cellSize();
        int baseX = gridX * cell + cell / 2;
        int baseZ = gridZ * cell + cell / 2;
        int centerX = baseX + random.nextInt(2 * MAX_JITTER + 1) - MAX_JITTER;
        int centerZ = baseZ + random.nextInt(2 * MAX_JITTER + 1) - MAX_JITTER;
        int centerY = HEIGHT_LAYERS[random.nextInt(HEIGHT_LAYERS.length)];
        // Fit the chosen layer into the band this island's own size can occupy, so neither the build limit nor
        // the world floor can cut it flat. A bigger island is thicker, so this pushes big islands up and leaves
        // small ones where their layer asked for - which is what makes it possible to keep headroom at both
        // ends at once (user 2026-10-11: "生成高度…需要留些余量，比如默认最高只到128" and then "底部也是需要
        // 留出余量"). The margins (8 blocks by default) come from BrokenIslandChunkGenerator's config.
        int rise = 8 + (int) Math.round(radius * 0.02);
        int depth = 8 + 70 + (int) Math.round(radius * 0.42);
        int floor = BrokenIslandChunkGenerator.MIN_Y + BrokenIslandChunkGenerator.BOTTOM_MARGIN + depth;
        // The ceiling also reserves the band structures stand in, because a structure grows upwards and there is
        // no way to know how tall a mod's one will be (see BrokenIslandChunkGenerator#STRUCTURE_HEADROOM).
        int ceiling = BrokenIslandChunkGenerator.MIN_Y + BrokenIslandChunkGenerator.GEN_DEPTH
                - BrokenIslandChunkGenerator.TOP_MARGIN - BrokenIslandChunkGenerator.STRUCTURE_HEADROOM - rise;
        if (floor <= ceiling) {
            centerY = Math.max(floor, Math.min(ceiling, centerY));
        }
        int biomeIndex = random.nextInt(1 << 20);
        int variant = random.nextInt(1 << 16);
        // 1.0 = round, up to 2.2 = clearly elongated; the angle keeps them from all pointing the same way.
        double stretch = 1.0 + random.nextDouble() * (MAX_STRETCH - 1.0);
        double angle = random.nextDouble() * Math.PI;
        return new Island(gridX, gridZ, centerX, centerZ, radius, centerY, biomeIndex, variant, stretch, angle);
    }

    /** The grid cell a block column falls into. */
    public static int gridOf(int blockX, int blockZ) {
        return Math.floorDiv(blockX, cellSize());
    }

    /** The island that owns the given block column (whether or not it actually reaches it). */
    public static Island islandFor(long seed, int blockX, int blockZ) {
        return nearest(seed, blockX, blockZ, 0.0);
    }

    /**
     * The island whose rim, inflated by {@code margin} blocks, covers this column, or {@code null}.
     * <p>
     * This exists because biomes are resolved on a 4x4 grid: a quad is answered with a single biome,
     * so testing only the quad's corner makes quads that merely <i>straddle</i> the rim answer "void".
     * Features look up the biome per column, so those quads inside the island came out as void - which
     * is exactly why snow (and anything else biome driven) had holes along island edges. Covering a
     * little too much is harmless (the extra quads sit over void where features find no ground), while
     * covering too little leaves the holes, so the biome side deliberately errs outwards.
     */
    public static Island islandCovering(long seed, int blockX, int blockZ, double margin) {
        return nearest(seed, blockX, blockZ, margin);
    }

    private static Island nearest(long seed, int blockX, int blockZ, double margin) {
        int cell = cellSize();
        int gridX = Math.floorDiv(blockX - cell / 2 + MAX_JITTER, cell);
        int gridZ = Math.floorDiv(blockZ - cell / 2 + MAX_JITTER, cell);
        // The jitter can put an island just over the cell border, so also consider the four
        // neighbours and return the nearest one that reaches the column.
        Island best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Island candidate = islandAt(seed, gridX + dx, gridZ + dz);
                double distance = candidate.normalizedDistance(blockX, blockZ);
                // Measure the margin against the *minor* semi-axis: normalizedDistance mixes both axes,
                // so using the major one would leave only margin/stretch blocks of coverage in the
                // narrow direction (which is how chunks that touch an island sideways kept being
                // missed, and with them their snow).
                double minor = candidate.radius() / candidate.stretch();
                if (distance * minor <= minor + margin && distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /** Deterministic 64 bit mix of the seed and the grid coordinates. */
    private static long mix(long seed, int gridX, int gridZ) {
        long h = seed;
        h ^= gridX * 0x9E3779B97F4A7C15L;
        h ^= gridZ * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return h;
    }

    // ------------------------------------------------------------------ debug dump

    /** Log the layout of a sample of islands at server start (becomes a config key). */
    public static boolean logLayout = true;

    /** Dumps a sample of islands plus the checks that matter: gap floor and determinism. */
    public static void debugLog(long seed) {
        if (!logLayout) {
            return;
        }
        java.util.List<Island> sample = new java.util.ArrayList<>();
        for (int gx = -2; gx <= 2; gx++) {
            for (int gz = -2; gz <= 2; gz++) {
                sample.add(islandAt(seed, gx, gz));
            }
        }
        int minRadius = Integer.MAX_VALUE;
        int maxRadius = Integer.MIN_VALUE;
        for (Island island : sample) {
            minRadius = Math.min(minRadius, island.radius());
            maxRadius = Math.max(maxRadius, island.radius());
        }
        double worstGap = Double.MAX_VALUE;
        for (int i = 0; i < sample.size(); i++) {
            for (int j = i + 1; j < sample.size(); j++) {
                Island a = sample.get(i);
                Island b = sample.get(j);
                worstGap = Math.min(worstGap, Math.hypot(a.centerX() - b.centerX(), a.centerZ() - b.centerZ())
                        - a.radius() - b.radius());
            }
        }
        boolean deterministic = true;
        for (Island island : sample) {
            deterministic &= island.equals(islandAt(seed, island.gridX(), island.gridZ()));
        }
        Island first = sample.get(0);
        FlightRingMod.LOGGER.info("[ISLANDS] seed = {}, cell = {}, gap range = {}..{}, radius range = {}..{}",
                seed, cellSize(), GAP_MIN, GAP_MAX, MIN_RADIUS, MAX_RADIUS);
        FlightRingMod.LOGGER.info("[ISLANDS] sample grid({},{}) center=({},{}) y={} radius={} biomeIndex={}",
                first.gridX(), first.gridZ(), first.centerX(), first.centerZ(),
                first.centerY(), first.radius(), first.biomeIndex());
        FlightRingMod.LOGGER.info("[ISLANDS] radius spread over 25 islands = {}..{}, smallest gap = {} (floor {})",
                minRadius, maxRadius, Math.round(worstGap), GAP_MIN);
        FlightRingMod.LOGGER.info("[ISLANDS] deterministic for this seed = {}", deterministic);

        // Where is each pool biome? With a pool of this size a specific biome is rare - one island in
        // poolSize carries it - so "/locate biome", which only searches a few thousand blocks, usually
        // misses it. This table answers "is it too far, or not generating at all?" directly: every biome
        // gets its nearest island within the scanned square, and the ones that never came up are listed.
        java.util.List<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> pool =
                BrokenBiomePool.cachedPool();
        if (pool.isEmpty()) {
            return;
        }
        int cells = 40;
        java.util.Map<String, Island> nearest = new java.util.LinkedHashMap<>();
        for (int gx = -cells; gx <= cells; gx++) {
            for (int gz = -cells; gz <= cells; gz++) {
                Island island = islandAt(seed, gx, gz);
                String biome = biomeIdAt(pool, island);
                if (biome == null) {
                    continue;
                }
                Island previous = nearest.get(biome);
                if (previous == null || Math.hypot(island.centerX(), island.centerZ())
                        < Math.hypot(previous.centerX(), previous.centerZ())) {
                    nearest.put(biome, island);
                }
            }
        }
        FlightRingMod.LOGGER.info("[ISLANDS] nearest island per biome within {} cells ({} blocks):",
                cells, cells * cellSize());
        nearest.forEach((biome, island) -> FlightRingMod.LOGGER.info(
                "[ISLANDS]   {} @grid({},{}) centre=({},{}) dist={} blocks",
                biome, island.gridX(), island.gridZ(), island.centerX(), island.centerZ(),
                Math.round(Math.hypot(island.centerX(), island.centerZ()))));
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome : pool) {
            String id = biome.unwrapKey().map(key -> key.location().toString()).orElse(null);
            if (id != null && !nearest.containsKey(id)) {
                missing.add(id);
            }
        }
        FlightRingMod.LOGGER.info("[ISLANDS] not found within {} blocks: {} ({} of {} biomes)",
                cells * cellSize(), missing, missing.size(), pool.size());
    }

    /** The biome an island uses, as a string, or {@code null} before the pool exists. */
    private static String biomeIdAt(java.util.List<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> pool,
                                    Island island) {
        if (pool.isEmpty() || island == null) {
            return null;
        }
        return pool.get(Math.floorMod(island.biomeIndex(), pool.size()))
                .unwrapKey().map(key -> key.location().toString()).orElse(null);
    }
}
