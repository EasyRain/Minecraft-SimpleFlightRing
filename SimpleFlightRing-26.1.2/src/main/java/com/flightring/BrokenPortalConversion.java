package com.flightring;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Rewrites a lit nether portal into a broken portal: right-clicking the portal while carrying the
 * ender ring (not sneaking) turns every block of that one portal into
 * {@link ModBlocks#BROKEN_PORTAL} and plays the vanilla portal-shatter sound.
 * <p>
 * Only the connected portal that was clicked is touched - a second portal standing right next to it
 * keeps working as a nether portal. Nothing is announced in chat: the hint players get is the
 * separate one-time line in {@link BrokenRingHint}, and the rest is for them to notice.
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
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!isEnderRing(event.getItemStack())) {
            return;
        }
        if (rewrite(level, event.getPos()) == 0) {
            return;
        }
        // Whoever rewrites a portal is in the know: the chat hint is for everybody else.
        BrokenPortalData.setKnowsDimension(event.getEntity());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
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

    /** The ender ring is the only ring with the warp ability. */
    private static boolean isEnderRing(ItemStack stack) {
        return stack.getItem() instanceof FlightRingItem ring && ring.hasAbility(RingAbility.WARP_NEXUS);
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
