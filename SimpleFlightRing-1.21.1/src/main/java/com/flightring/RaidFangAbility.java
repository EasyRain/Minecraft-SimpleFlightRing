package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The ability key of the raid relic ring: the evoker's fang line, summoned in front of the
 * wearer. Copied from the far-range branch of vanilla's {@code Evoker#performSpellCasting}: up
 * to 16 fangs march away from the caster in a straight line, each one spawning
 * {@code 1.25 * (i + 1)} blocks further out with a warmup delay of {@code i} ticks - so the line
 * travels outwards instead of appearing at once. The angle is taken from the wearer's own look
 * direction (a player has no target to aim at), and the per-fang placement is vanilla's
 * {@code Evoker#createSpellEntity} verbatim, including its {@code GameEvent.ENTITY_PLACE} call
 * so sculk sensors and the like still notice the spell.
 */
public final class RaidFangAbility {

    /** Internal cooldown of the fang line, like the sculk and miner rings' ability key. */
    private static final int FANG_COOLDOWN_TICKS = 60;
    /** Vanilla's far-range branch spawns 16 fangs, reaching 1.25 * 16 = 20 blocks. */
    private static final int FANG_COUNT = 16;
    /** Horizontal distance between two fangs, exactly vanilla's step. */
    private static final double FANG_SPACING = 1.25;

    /** The ability key's internal cooldown, per player. */
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    /**
     * Summons the fang line for the worn raid ring, if the ability is off cooldown. Called by
     * {@link RingAbilityKeyHandler} when the ability key is pressed.
     */
    static void tryCast(ServerPlayer player, ItemStack ring) {
        long now = player.serverLevel().getGameTime();
        Long readyAt = READY_AT.get(player.getUUID());
        if (readyAt != null && now < readyAt) {
            long seconds = Math.max(1L, (readyAt - now + 19L) / 20L);
            player.displayClientMessage(Component.translatable(
                    "message.simpleflightring.raid_fang_cooldown", seconds).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        READY_AT.put(player.getUUID(), now + FANG_COOLDOWN_TICKS);
        castFangs(player);
        FlightRingMod.LOGGER.debug("[FlightRing] {} summoned the evoker's fangs with the raid ring",
                player.getName().getString());
    }

    /** Vanilla's far-range fang line, aimed along the wearer's line of sight. */
    private static void castFangs(ServerPlayer player) {
        float angle = (float) Mth.atan2(player.getLookAngle().z, player.getLookAngle().x);
        double minY = player.getY();
        double maxY = player.getY() + 1.0;
        for (int i = 0; i < FANG_COUNT; i++) {
            double reach = FANG_SPACING * (i + 1);
            createSpellEntity(player,
                    player.getX() + Mth.cos(angle) * reach,
                    player.getZ() + Mth.sin(angle) * reach,
                    minY, maxY, angle, i);
        }
    }

    /**
     * Vanilla {@code Evoker.RaiderSpellCastingGoal#createSpellEntity} verbatim: walk down from
     * the wanted height until a block with a sturdy top face is found, then spawn the fangs on
     * top of whatever stands there.
     */
    private static void createSpellEntity(ServerPlayer caster, double x, double z,
                                          double minY, double maxY, float angle, int delayTicks) {
        ServerLevel level = caster.serverLevel();
        BlockPos pos = BlockPos.containing(x, maxY, z);
        boolean found = false;
        double topOffset = 0.0;

        do {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.isFaceSturdy(level, below, Direction.UP)) {
                if (!level.isEmptyBlock(pos)) {
                    BlockState state = level.getBlockState(pos);
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (!shape.isEmpty()) {
                        topOffset = shape.max(Direction.Axis.Y);
                    }
                }
                found = true;
                break;
            }
            pos = pos.below();
        } while (pos.getY() >= Mth.floor(minY) - 1);

        if (found) {
            level.addFreshEntity(new EvokerFangs(level, x, pos.getY() + topOffset, z, angle, delayTicks, caster));
            level.gameEvent(GameEvent.ENTITY_PLACE, new Vec3(x, pos.getY() + topOffset, z),
                    GameEvent.Context.of(caster));
        }
    }

    /**
     * Fired when the wearer leaves, so a returning player starts with a clean cooldown. Called
     * from {@link RaidPlunderAbility}, which already listens for the logout and clone events.
     */
    static void clear(UUID playerId) {
        READY_AT.remove(playerId);
    }

    private RaidFangAbility() {
    }
}
