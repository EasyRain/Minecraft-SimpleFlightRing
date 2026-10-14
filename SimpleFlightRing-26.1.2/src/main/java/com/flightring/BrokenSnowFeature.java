package com.flightring;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Snow layers and ice for the broken dimension - our replacement for vanilla's
 * {@code minecraft:freeze_top_layer}.
 * <p>
 * It does exactly what {@code SnowAndFreezeFeature} does (snow layer where the biome wants snow, ice
 * where it wants freezing, and the {@code snowy} flag on the block underneath), but with two
 * differences that matter here:
 * <ul>
 *   <li>it is injected through a biome modifier at {@code TOP_LAYER_MODIFICATION}, i.e. after
 *       vegetation, exactly like the vanilla one - so trees are placed on grass first and the snow
 *       lands on top of them;</li>
 *   <li>its placed feature carries no placement modifiers, so it runs once per chunk from the chunk
 *       origin and then walks the whole 16x16 itself. Vanilla's version has a bare {@code BiomeFilter},
 *       which is evaluated at that same origin: because the island biome no longer overhangs the
 *       island (that overhang was what let features act in the void), the vanilla gate fails on the
 *       chunks whose origin lands in the void and those chunks lost their snow. Ours does not care
 *       where the origin is.</li>
 * </ul>
 * The biome modifier is applied to the global biome registry, so this feature would also run in other
 * dimensions; the dimension guard below keeps it inside ours.
 */
public class BrokenSnowFeature extends Feature<NoneFeatureConfiguration> {

    private static final ResourceKey<Level> THE_BROKEN =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "the_broken"));

    public BrokenSnowFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!(level.getLevel() instanceof ServerLevel server) || !server.dimension().equals(THE_BROKEN)) {
            return false;
        }
        BlockPos origin = context.origin();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        int baseX = origin.getX() & ~15;
        int baseZ = origin.getZ() & ~15;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = baseX + x;
                int worldZ = baseZ + z;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ);
                pos.set(worldX, top, worldZ);
                below.set(pos).move(Direction.DOWN, 1);
                Biome biome = level.getBiome(pos).value();
                if (biome.shouldFreeze(level, below, false)) {
                    level.setBlock(below, Blocks.ICE.defaultBlockState(), 2);
                }
                if (biome.shouldSnow(level, pos)) {
                    level.setBlock(pos, Blocks.SNOW.defaultBlockState(), 2);
                    BlockState state = level.getBlockState(below);
                    if (state.hasProperty(SnowyBlock.SNOWY)) {
                        level.setBlock(below, state.setValue(SnowyBlock.SNOWY, Boolean.TRUE), 2);
                    }
                }
            }
        }
        return true;
    }
}
