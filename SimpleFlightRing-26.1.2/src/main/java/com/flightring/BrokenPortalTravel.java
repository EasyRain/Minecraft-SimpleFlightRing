package com.flightring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Where a rewritten portal sends you, and how the other side is prepared.
 * <p>
 * The overworld/nether side is remembered per player (see {@link BrokenPortalData}); the broken
 * dimension side is a small obsidian platform with a freshly built portal, because that dimension
 * is still completely empty while the islands are being designed. A portal is only built when there
 * is none near the target position, so the trip is stable once it exists.
 */
public final class BrokenPortalTravel {

    /** The hidden dimension, see {@code data/simpleflightring/dimension/the_broken.json}. */
    public static final ResourceKey<Level> BROKEN_DIMENSION = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "the_broken"));

    /**
     * Search radii, deliberately <b>the same in every dimension</b>. Vanilla uses 16 in the nether and
     * 128 in the overworld because those two dimensions are scaled 1:8 against each other - that
     * asymmetry belongs to the nether and must not be copied here: the broken dimension sits 1:1 next
     * to the overworld, and this mod never scales coordinates (it remembers the exact entry portal
     * instead), so both directions get the same reach.
     */
    private static final int CLOSE_SEARCH_RADIUS = 16;
    private static final int WIDE_SEARCH_RADIUS = 128;

    /** Inner size of a minimal nether portal: 2 wide, 3 tall. */
    private static final int INNER_WIDTH = 2;
    private static final int INNER_HEIGHT = 3;

    private BrokenPortalTravel() {
    }

    /**
     * Sends an entity that is not in the broken dimension yet over there.
     * <p>
     * World shared, exactly like the nether: the destination follows from the <b>coordinates</b> of the
     * portal that was stepped into (this dimension sits 1:1 next to the overworld, so nothing is
     * scaled), never from per player memory - so one portal always leads to the same place for
     * everybody, and the counterpart is searched for and reused instead of being rebuilt per player.
     */
    public static TeleportTransition toBroken(ServerLevel from, Entity entity, BlockPos portalPos) {
        ServerLevel target = from.getServer().getLevel(BROKEN_DIMENSION);
        if (target == null) {
            return null;
        }
        // The only per player bit that is kept, and only because a nether portal in the nether can be
        // converted too: the way back has to know which dimension it came from.
        BrokenPortalData.setOriginDimension(entity, from.dimension().identifier());
        BrokenPortalFrames.Rect rect = findOrBuild(target, portalPos, axisOf(from.getBlockState(portalPos)));
        return transition(target, rect, entity);
    }

    /**
     * Sends an entity back to the dimension it came from, at this portal's own coordinates - the
     * counterpart of a world shared portal is simply the portal at the same place in the other
     * dimension (1:1), which is what makes the pair stable without storing anything.
     */
    public static TeleportTransition backToOrigin(ServerLevel from, Entity entity, BlockPos portalPos) {
        Identifier originId = BrokenPortalData.originDimension(entity);
        ServerLevel target = originId == null
                ? from.getServer().overworld()
                : from.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, originId));
        if (target == null) {
            target = from.getServer().overworld();
        }
        BrokenPortalFrames.Rect rect = findOrBuild(target, portalPos, axisOf(from.getBlockState(portalPos)));
        return transition(target, rect, entity);
    }

    private static TeleportTransition transition(ServerLevel level, BrokenPortalFrames.Rect rect, Entity entity) {
        return new TeleportTransition(
                level,
                new Vec3(rect.spawnX(), rect.spawnY(), rect.spawnZ()),
                Vec3.ZERO,
                entity.getYRot(),
                entity.getXRot(),
                TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET));
    }

    private static Direction.Axis axisOf(BlockState state) {
        return state.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Direction.Axis.X);
    }

    // ------------------------------------------------------------------ finding / building

    private static BrokenPortalFrames.Rect findOrBuild(ServerLevel level, BlockPos wanted, Direction.Axis axis) {
        BrokenPortalFrames.Rect existing = find(level, wanted);
        return existing != null ? existing : build(level, wanted, axis);
    }

    /**
     * Looks for an existing rewritten portal around the wanted position, the way vanilla's
     * {@code PortalForcer#findClosestPortalPosition} does it: the whole area is loaded first (an
     * unloaded chunk would look exactly like "no portal here"), then the closest portal within the
     * radius wins, ties broken by the lowest Y.
     * <p>
     * The recorded origin is exact, so the cheap close range is tried first; the wide, vanilla-sized
     * range is the fallback for when that portal is gone and another one nearby should be reused
     * instead of building a new one.
     */
    private static BrokenPortalFrames.Rect find(ServerLevel level, BlockPos wanted) {
        BrokenPortalFrames.Rect near = scanForPortal(level, wanted, CLOSE_SEARCH_RADIUS);
        if (near != null) {
            return near;
        }
        // Same radius in every dimension: see CLOSE_SEARCH_RADIUS / WIDE_SEARCH_RADIUS.
        return scanForPortal(level, wanted, WIDE_SEARCH_RADIUS);
    }

    /** Closest rewritten portal (ties: lowest Y) in a square of the given radius, or {@code null}. */
    private static BrokenPortalFrames.Rect scanForPortal(ServerLevel level, BlockPos wanted, int radius) {
        for (int cx = (wanted.getX() - radius) >> 4; cx <= (wanted.getX() + radius) >> 4; cx++) {
            for (int cz = (wanted.getZ() - radius) >> 4; cz <= (wanted.getZ() + radius) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        WorldBorder border = level.getWorldBorder();
        double maxDistance = (double) radius * radius;
        List<BlockPos> found = new ArrayList<>();
        for (int cx = (wanted.getX() - radius) >> 4; cx <= (wanted.getX() + radius) >> 4; cx++) {
            for (int cz = (wanted.getZ() - radius) >> 4; cz <= (wanted.getZ() + radius) >> 4; cz++) {
                level.getChunk(cx, cz).findBlocks(
                        state -> state.is(ModBlocks.BROKEN_PORTAL.get()),
                        (pos, state) -> {
                            if (border.isWithinBounds(pos) && pos.distSqr(wanted) <= maxDistance) {
                                found.add(pos.immutable());
                            }
                        });
            }
        }
        return found.stream()
                .min(Comparator.<BlockPos>comparingDouble(pos -> pos.distSqr(wanted)).thenComparingInt(BlockPos::getY))
                .map(pos -> rectOf(level, pos))
                .orElse(null);
    }

    /**
     * Measures the portal the given block belongs to. Uses the shared measurement (air, fire,
     * vanilla portal blocks and our own blocks all count as the portal's inside), so a portal that
     * is still being converted - or one that lost a block - is still recognised as a whole.
     */
    private static BrokenPortalFrames.Rect rectOf(ServerLevel level, BlockPos any) {
        return BrokenPortalFrames.measure(level, any, axisOf(level.getBlockState(any)));
    }

    /**
     * Builds the arrival portal.
     * <p>
     * In a normal dimension the frame is simply stood on the local ground - the portal's own bottom
     * row is then the floor the player lands on, so no extra pad is needed. Only where there is
     * nothing to stand on at all (the broken dimension, or an empty world) does it get a pad: the
     * bottom row widened by one block to either side, i.e. a plain 4x3 obsidian pad with the portal
     * in the middle (user's request, 2026-10-09).
     */
    private static BrokenPortalFrames.Rect build(ServerLevel level, BlockPos wanted, Direction.Axis axis) {
        int y = Mth.clamp(wanted.getY(), level.getMinY() + 8, level.getMaxY() - 8);
        int x0 = wanted.getX();
        int z0 = wanted.getZ();

        // The destination chunk has to exist before anything can be placed (vanilla PortalForcer
        // does the same): this also generates it when the dimension is still empty.
        level.getChunkAt(new BlockPos(x0, y, z0));

        int w = INNER_WIDTH + 2;
        int h = INNER_HEIGHT + 2;

        // Whether a pad is needed is decided purely by the ground under the frame, in every
        // dimension: the broken dimension is empty today, but it will have sky islands later, and a
        // portal that happens to open on one of those must not get a plank under it either.
        int surface = findSurface(level, x0, z0, axis, y, w);
        boolean needsPad = surface < 0;
        if (!needsPad) {
            y = surface;
        }
        int perpFrom = needsPad ? -1 : 0;
        int perpTo = needsPad ? 1 : 0;

        // Everything is written inside a bulk edit: the portal only becomes valid once its whole
        // surface is in place, so the validity check must not run while this is half built.
        BrokenPortalFrames.beginBulkEdit();
        try {
            // Clear just the portal's own footprint (the pad's extra rows, one block above the top
            // frame), so the frame is never buried in terrain when returning to the overworld.
            for (int along = 0; along < w; along++) {
                for (int perp = perpFrom; perp <= perpTo; perp++) {
                    for (int up = 0; up <= h; up++) {
                        set(level, framePos(x0, z0, axis, along, y + up, perp), Blocks.AIR.defaultBlockState());
                    }
                }
            }

            // Pad only where there is nothing to stand on: bottom frame row plus one block in front
            // and one behind (4x3), portal in the middle.
            if (needsPad) {
                for (int along = 0; along < w; along++) {
                    for (int perp = perpFrom; perp <= perpTo; perp++) {
                        set(level, framePos(x0, z0, axis, along, y, perp), Blocks.OBSIDIAN.defaultBlockState());
                    }
                }
            }

            // Obsidian frame: 4 wide (2x3 inner) x 5 tall, exactly like a minimal nether portal.
            for (int i = 0; i < w; i++) {
                set(level, framePos(x0, z0, axis, i, y, 0), Blocks.OBSIDIAN.defaultBlockState());
                set(level, framePos(x0, z0, axis, i, y + h - 1, 0), Blocks.OBSIDIAN.defaultBlockState());
            }
            for (int j = 0; j < h; j++) {
                set(level, framePos(x0, z0, axis, 0, y + j, 0), Blocks.OBSIDIAN.defaultBlockState());
                set(level, framePos(x0, z0, axis, w - 1, y + j, 0), Blocks.OBSIDIAN.defaultBlockState());
            }

            // The portal itself.
            BlockState portal = ModBlocks.BROKEN_PORTAL.get().defaultBlockState()
                    .setValue(BrokenPortalBlock.AXIS, axis);
            for (int i = 1; i <= INNER_WIDTH; i++) {
                for (int j = 1; j <= INNER_HEIGHT; j++) {
                    set(level, framePos(x0, z0, axis, i, y + j, 0), portal);
                }
            }
        } finally {
            BrokenPortalFrames.endBulkEdit();
        }
        return new BrokenPortalFrames.Rect(framePos(x0, z0, axis, 1, y + 1, 0), axis, INNER_WIDTH, INNER_HEIGHT);
    }

    /**
     * Y at which the frame's bottom row would rest on something solid, or -1 when the whole column
     * below the wanted position is empty (a void world) - in that case the portal gets its own pad.
     * The search runs down to the world bottom on purpose: a portal that is built where the player
     * happened to be flying should still end up on the ground, not floating with a plank under it.
     */
    private static int findSurface(ServerLevel level, int x0, int z0, Direction.Axis axis, int y, int w) {
        int lowest = level.getMinY() + 2;
        for (int probe = y; probe > lowest; probe--) {
            if (hasGroundAt(level, x0, z0, axis, probe, w)) {
                return probe;
            }
        }
        return -1;
    }

    /** True when every position under the frame's bottom row is solid, non-liquid ground. */
    private static boolean hasGroundAt(ServerLevel level, int x0, int z0, Direction.Axis axis, int y, int w) {
        for (int along = 0; along < w; along++) {
            BlockState state = level.getBlockState(framePos(x0, z0, axis, along, y - 1, 0));
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Position in the portal's own coordinates: {@code along} runs along the portal axis, {@code up}
     * upwards and {@code perp} perpendicular to the portal surface (0 = the portal plane).
     */
    private static BlockPos framePos(int x0, int z0, Direction.Axis axis, int along, int up, int perp) {
        return axis == Direction.Axis.X
                ? new BlockPos(x0 + along, up, z0 + perp)
                : new BlockPos(x0 + perp, up, z0 + along);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }
}
