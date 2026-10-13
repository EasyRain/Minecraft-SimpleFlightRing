package com.flightring;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The portal that leads to the hidden broken dimension.
 * <p>
 * It is a deliberate copy of {@code NetherPortalBlock}: same obsidian frame rule, same walk-in
 * behaviour, same transition delay (game rules included), same confusion screen, same ambient sound
 * and particles, same "breaks when the frame is broken" logic. Only
 * {@link #getPortalDestination} differs - instead of the nether it links to
 * {@link BrokenPortalTravel#BROKEN_DIMENSION}. Zombified piglin spawning on random ticks is left
 * out on purpose: the broken dimension should not be reachable by piglins.
 */
public class BrokenPortalBlock extends Block implements Portal {

    public static final MapCodec<BrokenPortalBlock> CODEC = simpleCodec(BrokenPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    protected static final VoxelShape X_AXIS_AABB = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    protected static final VoxelShape Z_AXIS_AABB = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);
    /** Vanilla allows portals up to 21x21; the walk while measuring is bounded by this. */
    private static final int MAX_FRAME = 21;

    public BrokenPortalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    public MapCodec<BrokenPortalBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_AXIS_AABB : X_AXIS_AABB;
    }

    /**
     * Keeps the portal alive only while its obsidian frame is intact.
     * <p>
     * The vanilla check ({@code new PortalShape(...).isComplete()}) cannot be reused: PortalShape
     * counts air, fire and {@code minecraft:nether_portal} as "inside a portal" and everything else
     * as an obstruction, so it would treat our own blocks as a blockage and delete them the first
     * time a neighbour updates. {@link BrokenPortalFrames#isComplete} applies the same contract with
     * our own predicate instead: the frame has to be a portal frame (obsidian by default) and every
     * inner position has to be filled - which is what makes the whole portal collapse once one of
     * its blocks is removed, exactly like the nether portal.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (BrokenPortalFrames.isBulkEditing()) {
            // A portal is being written right now: never break blocks halfway through.
            return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
        }
        BrokenPortalFrames.Rect rect = BrokenPortalFrames.measure(level, pos, state.getValue(AXIS));
        if (rect == null) {
            // Still being built (no 2x3 of portal surface yet): keep the block.
            return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
        }
        return BrokenPortalFrames.isComplete(level, rect)
                ? super.updateShape(state, direction, neighbour, level, pos, neighbourPos)
                : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return entity instanceof Player player
                ? Math.max(1, level.getGameRules().getInt(player.getAbilities().invulnerable
                        ? GameRules.RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY
                        : GameRules.RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY))
                : 0;
    }

    @Nullable
    @Override
    public DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        return level.dimension().equals(BrokenPortalTravel.BROKEN_DIMENSION)
                ? BrokenPortalTravel.backToOrigin(level, entity, pos)
                : BrokenPortalTravel.toBroken(level, entity, pos);
    }

    @Override
    public Portal.Transition getLocalTransition() {
        return Portal.Transition.CONFUSION;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS,
                    0.5F, random.nextFloat() * 0.4F + 0.8F, false);
        }

        for (int i = 0; i < 4; i++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            double dx = (random.nextFloat() - 0.5) * 0.5;
            double dy = (random.nextFloat() - 0.5) * 0.5;
            double dz = (random.nextFloat() - 0.5) * 0.5;
            int side = random.nextInt(2) * 2 - 1;
            if (!level.getBlockState(pos.west()).is(this) && !level.getBlockState(pos.east()).is(this)) {
                x = pos.getX() + 0.5 + 0.25 * side;
                dx = random.nextFloat() * 2.0F * side;
            } else {
                z = pos.getZ() + 0.5 + 0.25 * side;
                dz = random.nextFloat() * 2.0F * side;
            }
            // Our own wisp: vanilla's portal particle motion (hovering, shrinking, fading away)
            // with a light gray tint instead of vanilla's purple - see BrokenPortalParticle.
            level.addParticle(ModParticles.BROKEN_PORTAL.get(), x, y, z, dx, dy, dz);
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case COUNTERCLOCKWISE_90, CLOCKWISE_90 -> switch (state.getValue(AXIS)) {
                case Z -> state.setValue(AXIS, Direction.Axis.X);
                case X -> state.setValue(AXIS, Direction.Axis.Z);
                default -> state;
            };
            default -> state;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }
}
