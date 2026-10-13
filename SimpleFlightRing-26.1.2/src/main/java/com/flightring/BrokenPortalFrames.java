package com.flightring;

import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Frame handling for {@link BrokenPortalBlock}.
 * <p>
 * Vanilla's {@code PortalShape} cannot be reused for a custom portal block: its "is this inside the
 * portal" test only accepts air, fire and {@code minecraft:nether_portal}, so a portal made of our
 * own blocks looks like an obstruction and the shape would always report as incomplete (which makes
 * the block delete itself on the next neighbour update - see the project notes).
 * <p>
 * This class measures the portal with our own predicate instead: <b>air, fire, a vanilla portal
 * block and our own portal block all count as inside</b>, so a portal is recognised while it is
 * still being converted, and the border is then validated with the NeoForge
 * {@code isPortalFrame} hook (obsidian by default), which keeps the vanilla frame rule intact.
 */
public final class BrokenPortalFrames {

    /** Vanilla allows portals up to 21x21; every walk is bounded by this. */
    public static final int MAX_SIZE = 21;

    /**
     * Depth of "we are writing a portal right now" sections. While this is positive, portal blocks
     * are never removed by {@link #isComplete}: a portal that is being built or converted is
     * transiently incomplete (and the vanilla blocks around it may vanish as they notice the new
     * block), so the validity check has to stay quiet until the whole surface has been written.
     */
    private static int bulkEdits = 0;

    public static void beginBulkEdit() {
        bulkEdits++;
    }

    public static void endBulkEdit() {
        bulkEdits = Math.max(0, bulkEdits - 1);
    }

    public static boolean isBulkEditing() {
        return bulkEdits > 0;
    }

    private BrokenPortalFrames() {
    }

    /** True for every block that can be part of the portal's inner surface. */
    public static boolean isPortalInterior(BlockState state) {
        return state.isAir()
                || state.is(BlockTags.FIRE)
                || state.is(Blocks.NETHER_PORTAL)
                || state.is(ModBlocks.BROKEN_PORTAL.get());
    }

    /**
     * Walks out the portal rectangle around the given position. Returns {@code null} when the
     * measured inside is smaller than a minimal portal (2x3), which is what happens while a portal
     * is still being built - callers keep the block in that case.
     */
    @Nullable
    public static Rect measure(BlockGetter level, BlockPos pos, Direction.Axis axis) {
        BlockPos min = pos;
        for (int i = 0; i < MAX_SIZE && isPortalInterior(level.getBlockState(min.relative(axis, -1))); i++) {
            min = min.relative(axis, -1);
        }
        for (int i = 0; i < MAX_SIZE && isPortalInterior(level.getBlockState(min.below())); i++) {
            min = min.below();
        }
        int width = 1;
        while (width < MAX_SIZE && isPortalInterior(level.getBlockState(min.relative(axis, width)))) {
            width++;
        }
        int height = 1;
        while (height < MAX_SIZE && isPortalInterior(level.getBlockState(min.above(height)))) {
            height++;
        }
        return width >= 2 && height >= 3 ? new Rect(min, axis, width, height) : null;
    }

    /** True while the portal frame around the measured rectangle is complete. */
    public static boolean frameIntact(BlockGetter level, Rect rect) {
        for (int i = 0; i < rect.width(); i++) {
            if (!isFrame(level, rect.min().relative(rect.axis(), i).below())
                    || !isFrame(level, rect.min().relative(rect.axis(), i).above(rect.height()))) {
                return false;
            }
        }
        for (int j = 0; j < rect.height(); j++) {
            if (!isFrame(level, rect.min().above(j).relative(rect.axis(), -1))
                    || !isFrame(level, rect.min().above(j).relative(rect.axis(), rect.width()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * True while every inner position still holds a portal block. This is what makes a portal
     * collapse as soon as one of its blocks is removed (the vanilla portal behaves the same way).
     * Vanilla portal blocks count as filled too, so a portal that is halfway through being
     * converted is not considered broken.
     */
    public static boolean interiorFilled(BlockGetter level, Rect rect) {
        for (int i = 0; i < rect.width(); i++) {
            for (int j = 0; j < rect.height(); j++) {
                BlockState state = level.getBlockState(rect.min().relative(rect.axis(), i).above(j));
                if (!state.is(Blocks.NETHER_PORTAL) && !state.is(ModBlocks.BROKEN_PORTAL.get())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The full validity test: intact frame <b>and</b> a filled inner surface - the same contract as
     * vanilla's {@code PortalShape#isComplete}.
     */
    public static boolean isComplete(BlockGetter level, Rect rect) {
        return frameIntact(level, rect) && interiorFilled(level, rect);
    }

    private static boolean isFrame(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).isPortalFrame(level, pos);
    }

    /** Bottom-left inner corner of a portal, its axis and its inner size. */
    public record Rect(BlockPos min, Direction.Axis axis, int width, int height) {

        /** Where an entity is put when it comes through: bottom centre, like the vanilla portal. */
        public double spawnX() {
            return axis == Direction.Axis.X ? min.getX() + width / 2.0 : min.getX() + 0.5;
        }

        public double spawnY() {
            return min.getY();
        }

        public double spawnZ() {
            return axis == Direction.Axis.X ? min.getZ() + 0.5 : min.getZ() + width / 2.0;
        }
    }
}
