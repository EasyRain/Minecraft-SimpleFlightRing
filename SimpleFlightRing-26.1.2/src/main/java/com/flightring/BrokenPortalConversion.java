package com.flightring;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Builds broken portals with a flight ring, in two ways:
 * <ul>
 *   <li><b>A lit nether portal</b> is rewritten into a broken portal: right-clicking it (not sneaking)
 *       turns every block of that one portal into {@link ModBlocks#BROKEN_PORTAL} and plays the vanilla
 *       portal-shatter sound. Only the connected portal that was clicked is touched - a second portal
 *       standing right next to it keeps working as a nether portal.</li>
 *   <li><b>An empty nether portal frame</b> gets a broken portal formed inside it (see
 *       {@link #formInFrame}). This is the way to build a portal in the broken dimension, where vanilla
 *       refuses to light a nether portal at all, and it also saves lighting one in the overworld first.</li>
 * </ul>
 * <b>Any flight ring works</b> - tiers, specials, relics and the linked ones alike. Nothing is announced
 * in chat: the hint players get is the separate one-time line in {@link BrokenRingHint}, and the rest is
 * for them to notice.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class BrokenPortalConversion {

    /** Safety cap while flood filling: a vanilla portal may be at most 21x21. */
    private static final int MAX_PORTAL_BLOCKS = 512;
    /** The shatter is played from at most this many positions, so big portals do not get deafening. */
    private static final int MAX_SOUNDS = 3;

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isShiftKeyDown()) {
            return;
        }
        if (!isFlightRing(event.getItemStack())) {
            return;
        }
        if (!isPortalAction(event.getLevel(), event.getPos(), event.getFace())) {
            return;
        }
        // Consume the click on BOTH sides, before touching the world: the client's Minecraft#startUseItem
        // only stops after a block interaction that returns SUCCESS or FAIL, and otherwise falls through to
        // the air use - which sends a ServerboundUseItemPacket and makes the server fire RightClickItem, the
        // event Curios equips a ring on. A server-only cancel (what this used to do) therefore still equipped
        // the ring while a portal was being built. With both sides cancelling, a portal click never equips,
        // while every click that is not a portal action falls through as usual and equips normally.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getLevel() instanceof ServerLevel level)) {
            // Client side: only the consumed click is predicted here, the server writes the blocks.
            return;
        }
        // A lit nether portal is rewritten (the original ritual); otherwise the ring forms a broken
        // portal inside an empty nether portal frame. That second path is the only way to build a way
        // home in the broken dimension, because vanilla refuses to light a nether portal outside the
        // overworld and the nether (BaseFireBlock#inPortalDimension).
        int converted = rewrite(level, event.getPos());
        if (converted == 0) {
            converted = formInFrame(level, event.getPos(), event.getFace());
        }
        if (converted > 0) {
            // Whoever builds a portal is in the know: the chat hint is for everybody else.
            BrokenPortalData.setKnowsDimension(event.getEntity());
        }
    }

    /**
     * True when right-clicking this block with a flight ring is a <b>portal action</b>: the click is going to
     * rewrite a lit nether portal, or form one inside a complete portal frame.
     * <p>
     * A frame click only counts when it comes through the face that looks <b>into</b> the opening - the top of
     * the bottom border, the underside of the top border, the inner side of a side column. Clicking a frame from
     * the outside (its top, its underside, its outer side, or its flat front) does nothing, the same way clicking
     * the frame instead of the opening does not light a portal in vanilla: assembling a portal stays deliberate,
     * and the ring is not consumed as a tool by a click that was aimed at the frame's exterior.
     * <p>
     * Pure test, no writes, so the client runs the very same check and cancels the click there too (see
     * {@link #onRightClickBlock} for why that matters). Both sides have to reach the same verdict, which is why
     * nothing here uses data the client does not have: the clicked block and its neighbours are always loaded.
     * Public for the same reason {@link #rewrite} is - probes can ask it without faking a right click.
     */
    public static boolean isPortalAction(LevelReader level, BlockPos clicked, Direction face) {
        BlockState state = level.getBlockState(clicked);
        if (state.is(Blocks.NETHER_PORTAL) || state.is(ModBlocks.BROKEN_PORTAL.get())) {
            return true;
        }
        return hasFrameAround(level, clicked.relative(face));
    }

    /** True when {@code inside} is the inner corner of a complete portal frame, in either horizontal axis. */
    private static boolean hasFrameAround(LevelReader level, BlockPos inside) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            BrokenPortalFrames.Rect rect = BrokenPortalFrames.measure(level, inside, axis);
            if (rect != null && BrokenPortalFrames.frameIntact(level, rect)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rewrites the whole portal the given block belongs to. Returns how many blocks were replaced,
     * or 0 when there is no lit nether portal there. Public so probes (and future content) can drive
     * it without faking a right click.
     */
    public static int rewrite(ServerLevel level, BlockPos clicked) {
        BlockState state = level.getBlockState(clicked);
        if (!state.is(Blocks.NETHER_PORTAL)) {
            return 0;
        }
        List<BlockPos> portal = collect(level, clicked, state);
        if (portal.isEmpty()) {
            return 0;
        }
        Direction.Axis axis = state.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(Direction.Axis.X);
        BlockState broken = ModBlocks.BROKEN_PORTAL.get().defaultBlockState()
                .setValue(BrokenPortalBlock.AXIS, axis);
        // While the portal is written block by block it is transiently incomplete (and the vanilla
        // blocks around it vanish as soon as they notice a foreign block), so the validity check has
        // to stay quiet until the whole surface is ours - otherwise the first blocks would delete
        // themselves again and leave holes.
        BrokenPortalFrames.beginBulkEdit();
        try {
            for (BlockPos pos : portal) {
                level.setBlock(pos, broken, Block.UPDATE_ALL);
            }
        } finally {
            BrokenPortalFrames.endBulkEdit();
        }
        playShatter(level, portal);
        return portal.size();
    }

    /**
     * Any flight ring works: the six tiers, the two special rings, the eight relics and the three linked
     * ones. The damaged loot variants are not rings one can use, so they do not count.
     */
    private static boolean isFlightRing(ItemStack stack) {
        return stack.getItem() instanceof FlightRingItem;
    }

    /**
     * Forms a broken portal inside an empty nether portal frame: the cell the clicked face looks at is
     * measured as a portal rectangle with our own frame rules (see {@link BrokenPortalFrames} - vanilla's
     * {@code PortalShape} would treat our own blocks as an obstruction), and it gets the portal blocks when
     * the frame is intact and not filled yet.
     * <p>
     * Only that one cell, and only the face the player aimed through: the same rule {@link #isPortalAction}
     * uses. Trying the other neighbours as well would let a click on a frame - even on one that is already a
     * portal - assemble a portal somewhere the player did not aim at.
     * <p>
     * Public for the same reason {@link #rewrite} is: probes (and future content) can drive it without
     * faking a right click.
     */
    public static int formInFrame(ServerLevel level, BlockPos clicked, @Nullable Direction clickedFace) {
        Direction front = clickedFace == null ? Direction.UP : clickedFace;
        return tryForm(level, clicked.relative(front));
    }

    /** Forms a portal in either horizontal axis at one inner position; returns 0 when there is none. */
    private static int tryForm(ServerLevel level, BlockPos inside) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            BrokenPortalFrames.Rect rect = BrokenPortalFrames.measure(level, inside, axis);
            if (rect == null || !BrokenPortalFrames.frameIntact(level, rect)) {
                continue;
            }
            if (BrokenPortalFrames.interiorFilled(level, rect)) {
                // Already a finished portal - a lit one was handled by rewrite() above, and a broken one
                // needs nothing.
                return 0;
            }
            return fillFrame(level, rect);
        }
        return 0;
    }

    /**
     * Writes the broken portal into a measured rectangle, in one batch so no block deletes itself while
     * the surface is still incomplete, and plays vanilla's portal-lighting sound. Nothing is shattered
     * here: the frame is being turned into a portal, it is the rewrite path that shatters a lit one.
     */
    private static int fillFrame(ServerLevel level, BrokenPortalFrames.Rect rect) {
        BlockState broken = ModBlocks.BROKEN_PORTAL.get().defaultBlockState()
                .setValue(BrokenPortalBlock.AXIS, rect.axis());
        BrokenPortalFrames.beginBulkEdit();
        try {
            for (int i = 0; i < rect.width(); i++) {
                for (int j = 0; j < rect.height(); j++) {
                    level.setBlock(rect.min().relative(rect.axis(), i).above(j), broken, Block.UPDATE_ALL);
                }
            }
        } finally {
            BrokenPortalFrames.endBulkEdit();
        }
        level.playSound(null, rect.min(), SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.6F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        return rect.width() * rect.height();
    }

    /** Flood fill of the connected portal blocks of one portal (same axis, same surface). */
    private static List<BlockPos> collect(ServerLevel level, BlockPos start, BlockState startState) {
        Direction.Axis axis = startState.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(Direction.Axis.X);
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && found.size() < MAX_PORTAL_BLOCKS) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.NETHER_PORTAL)) {
                continue;
            }
            if (state.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Direction.Axis.X) != axis) {
                continue;
            }
            found.add(pos);
            for (Direction direction : Direction.values()) {
                if (seen.add(pos.relative(direction))) {
                    queue.add(pos.relative(direction));
                }
            }
        }
        return found;
    }

    /** {@code GLASS_BREAK} is exactly the sound vanilla plays when a nether portal block breaks. */
    private static void playShatter(ServerLevel level, List<BlockPos> portal) {
        RandomSource random = level.getRandom();
        int step = Math.max(1, portal.size() / MAX_SOUNDS);
        for (int i = 0; i < portal.size() && i / step < MAX_SOUNDS; i += step) {
            level.playSound(null, portal.get(i), SoundEvents.GLASS_BREAK, SoundSource.BLOCKS,
                    0.7F, 0.9F + random.nextFloat() * 0.2F);
        }
    }

    private BrokenPortalConversion() {
    }
}
