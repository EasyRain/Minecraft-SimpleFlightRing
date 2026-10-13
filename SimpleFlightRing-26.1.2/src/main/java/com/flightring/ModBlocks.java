package com.flightring;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Blocks of the mod.
 * <p>
 * The only one so far is the portal that leads to the hidden "broken" dimension. It copies the
 * vanilla nether portal in every visible way (same textures/models, same obsidian frame rule, same
 * walk-in behaviour and delay, same sound and particles) and is registered without an item, exactly
 * like {@code minecraft:nether_portal} - the block id itself is what marks a portal as "rewritten",
 * so no extra bookkeeping is needed to remember which portals lead where.
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FlightRingMod.MODID);

    public static final DeferredBlock<Block> BROKEN_PORTAL = BLOCKS.registerBlock(
            "broken_portal",
            BrokenPortalBlock::new,
            properties -> properties
                    .noCollision()
                    .randomTicks()
                    .strength(-1.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 11)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK));

    private ModBlocks() {
    }
}
