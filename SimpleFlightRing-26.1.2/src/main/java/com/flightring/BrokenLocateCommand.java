package com.flightring;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /simpleflightring locate ...} - find islands, biomes and (later) structures.
 * <p>
 * The point of this command (user 2026-10-13: "locate 找群系最大找到过 15000+ 远的群系，但是消耗性能而且只能
 * 找一个，既然生成器都是我们自己的，我们应当能更好的找到更远更多") is that the broken dimension's layout is a
 * <b>pure function of {@code (seed, gridCell)}</b>: {@link BrokenIslandLayout#islandAt} needs no noise, no
 * chunks and no generating. So this walks grid cells in rings around the caller and computes each island's
 * biome index arithmetically - thousands of cells per tick, no chunk is ever loaded, and it can return the
 * nearest N matches instead of vanilla's single hit.
 * <p>
 * Deliberately server-only code: no client type may appear in a method descriptor of a class a dedicated
 * server loads (see the 2026-10-13 "invalid dist" crash).
 */
public final class BrokenLocateCommand {

    /** Every island in range has to be inside this many blocks of the caller. */
    private static final int DEFAULT_RADIUS = 20000;
    private static final int MAX_RADIUS_LIMIT = 200000;
    private static final int DEFAULT_LIMIT = 5;

    /** Tab completion for {@code locate biome}: only ids that are actually in this world's pool. */
    private static final SuggestionProvider<CommandSourceStack> BIOME_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (Holder<Biome> biome : BrokenBiomePool.cachedPool()) {
            biome.unwrapKey().map(key -> key.identifier().toString()).ifPresent(id -> {
                if (id.toLowerCase(java.util.Locale.ROOT).contains(remaining)) {
                    builder.suggest(id);
                }
            });
        }
        return builder.buildFuture();
    };

    /** Tab completion for the {@code locate island} filter. */
    private static final SuggestionProvider<CommandSourceStack> ISLAND_FILTER_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (String filter : new String[] {"any", "wet", "dry"}) {
            if (filter.startsWith(remaining)) {
                builder.suggest(filter);
            }
        }
        return builder.buildFuture();
    };

    private BrokenLocateCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("simpleflightring")
                // 26.x: CommandSourceStack#hasPermission(int) is gone - permissions are a PermissionSet, and
                // Commands exposes the same levels vanilla's own /locate uses.
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("locate")
                        .then(Commands.literal("biome")
                                .then(Commands.argument("biome", net.minecraft.commands.arguments.IdentifierArgument.id())
                                        .suggests(BIOME_SUGGESTIONS)
                                        .executes(ctx -> locateBiome(ctx, DEFAULT_RADIUS, DEFAULT_LIMIT))
                                        .then(Commands.argument("maxRadius", IntegerArgumentType.integer(64, MAX_RADIUS_LIMIT))
                                                .executes(ctx -> locateBiome(ctx, IntegerArgumentType.getInteger(ctx, "maxRadius"), DEFAULT_LIMIT))
                                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 64))
                                                        .executes(ctx -> locateBiome(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "maxRadius"),
                                                                IntegerArgumentType.getInteger(ctx, "limit")))))))
                        .then(Commands.literal("island")
                                .executes(ctx -> locateIsland(ctx, "any", DEFAULT_RADIUS, DEFAULT_LIMIT))
                                .then(Commands.argument("filter", StringArgumentType.word())
                                        .suggests(ISLAND_FILTER_SUGGESTIONS)
                                        .executes(ctx -> locateIsland(ctx, StringArgumentType.getString(ctx, "filter"), DEFAULT_RADIUS, DEFAULT_LIMIT))
                                        .then(Commands.argument("maxRadius", IntegerArgumentType.integer(64, MAX_RADIUS_LIMIT))
                                                .executes(ctx -> locateIsland(ctx, StringArgumentType.getString(ctx, "filter"),
                                                        IntegerArgumentType.getInteger(ctx, "maxRadius"), DEFAULT_LIMIT))
                                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 64))
                                                        .executes(ctx -> locateIsland(ctx, StringArgumentType.getString(ctx, "filter"),
                                                                IntegerArgumentType.getInteger(ctx, "maxRadius"),
                                                                IntegerArgumentType.getInteger(ctx, "limit")))))))
                        .then(Commands.literal("nearest")
                                .executes(ctx -> locateIsland(ctx, "any", MAX_RADIUS_LIMIT, 1)))));
    }

    /**
     * The islands only exist in our dimension (user 2026-10-14), so the command is refused everywhere else with
     * a message rather than being hidden - a player who mistypes the dimension should be told why.
     */
    private static boolean requireBrokenDimension(CommandSourceStack source) {
        if (source.getLevel().dimension().equals(BrokenPortalTravel.BROKEN_DIMENSION)) {
            return true;
        }
        source.sendFailure(Component.translatable("simpleflightring.command.locate.wrong_dimension"));
        return false;
    }

    /** {@code locate biome <id> [maxRadius] [limit]} - pure arithmetic on the grid. */
    private static int locateBiome(CommandContext<CommandSourceStack> ctx, int maxRadius, int limit) {
        CommandSourceStack source = ctx.getSource();
        if (!requireBrokenDimension(source)) {
            return 0;
        }
        Identifier wanted = net.minecraft.commands.arguments.IdentifierArgument.getId(ctx, "biome");
        List<Holder<Biome>> pool = BrokenBiomePool.cachedPool();
        if (pool.isEmpty()) {
            source.sendFailure(Component.translatable("simpleflightring.command.locate.no_pool"));
            return 0;
        }
        int index = indexOf(pool, wanted);
        if (index < 0) {
            source.sendFailure(Component.translatable("simpleflightring.command.locate.not_in_pool",
                    wanted.toString(), pool.size()));
            return 0;
        }
        return report(source, search(source, maxRadius, limit,
                island -> Math.floorMod(island.biomeIndex(), pool.size()) == index), maxRadius,
                Component.translatable("simpleflightring.command.locate.for_biome", wanted.toString()));
    }

    /** {@code locate island [any|wet|dry] [maxRadius] [limit]}. */
    private static int locateIsland(CommandContext<CommandSourceStack> ctx, String filter, int maxRadius, int limit) {
        CommandSourceStack source = ctx.getSource();
        if (!requireBrokenDimension(source)) {
            return 0;
        }
        Predicate<BrokenIslandLayout.Island> matches = switch (filter.toLowerCase(java.util.Locale.ROOT)) {
            case "wet" -> BrokenIslandChunkGenerator::wetIsland;
            case "dry" -> island -> !BrokenIslandChunkGenerator.wetIsland(island);
            default -> island -> true;
        };
        return report(source, search(source, maxRadius, limit, matches), maxRadius,
                Component.translatable("simpleflightring.command.locate.for_island", filter));
    }

    /** Ring-by-ring walk over the grid; nothing here touches a chunk. */
    private static List<BrokenIslandLayout.Island> search(CommandSourceStack source, int maxRadius, int limit,
                                                          Predicate<BrokenIslandLayout.Island> matches) {
        List<BrokenIslandLayout.Island> found = new ArrayList<>();
        long seed = BrokenIslandSeed.get();
        int cellSize = BrokenIslandLayout.cellSize();
        int originX = (int) source.getPosition().x;
        int originZ = (int) source.getPosition().z;
        int originGridX = Math.floorDiv(originX, cellSize);
        int originGridZ = Math.floorDiv(originZ, cellSize);
        int maxRing = maxRadius / cellSize + 2;
        for (int ring = 0; ring <= maxRing; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    // Only the shell of this ring is new; skipping the interior avoids re-testing cells.
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    BrokenIslandLayout.Island island =
                            BrokenIslandLayout.islandAt(seed, originGridX + dx, originGridZ + dz);
                    if (island == null || !matches.test(island)) {
                        continue;
                    }
                    if (distance(originX, originZ, island.centerX(), island.centerZ()) > maxRadius) {
                        continue;
                    }
                    found.add(island);
                }
            }
        }
        found.sort(Comparator.comparingLong(island ->
                distance(originX, originZ, island.centerX(), island.centerZ())));
        return found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
    }

    private static int report(CommandSourceStack source, List<BrokenIslandLayout.Island> found, int maxRadius,
                              Component what) {
        int originX = (int) source.getPosition().x;
        int originZ = (int) source.getPosition().z;
        if (found.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "simpleflightring.command.locate.none", what, maxRadius), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("simpleflightring.command.locate.header", found.size()), false);
        List<Holder<Biome>> pool = BrokenBiomePool.cachedPool();
        for (BrokenIslandLayout.Island island : found) {
            long dist = distance(originX, originZ, island.centerX(), island.centerZ());
            Component biome = pool.isEmpty()
                    ? Component.literal("?")
                    : pool.get(Math.floorMod(island.biomeIndex(), pool.size()))
                    .unwrapKey().map(key -> (Component) Component.literal(key.identifier().toString()))
                    .orElseGet(() -> Component.literal("?"));
            // Vanilla-style: only the green bracketed coordinates are clickable, and the tooltip is one short
            // line ("click to teleport") instead of a paragraph (user 2026-10-14). The bracket shows the two
            // horizontal axes and the radius; Y is the height, so it only appears in the teleport command.
            int targetY = island.centerY() + 30;
            Component coordinates = Component.translatable("simpleflightring.command.locate.coordinates",
                            island.centerX(), island.centerZ(), island.radius())
                    .withStyle(style -> style
                            .withColor(ChatFormatting.GREEN)
                            // 26.x: ClickEvent and HoverEvent became sealed interfaces whose records are the
                            // constructor - the (Action, value) pair is gone.
                            .withClickEvent(new ClickEvent.SuggestCommand(
                                    "/tp @s " + island.centerX() + " " + targetY + " " + island.centerZ()))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Component.translatable("simpleflightring.command.locate.click_hint"))));
            Component line = Component.translatable("simpleflightring.command.locate.entry", biome, dist)
                    .append(coordinates);
            source.sendSuccess(() -> line, false);
        }
        return found.size();
    }

    private static int indexOf(List<Holder<Biome>> pool, Identifier wanted) {
        for (int i = 0; i < pool.size(); i++) {
            Identifier id = pool.get(i).unwrapKey().map(key -> key.identifier()).orElse(null);
            if (wanted.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static long distance(int fromX, int fromZ, int toX, int toZ) {
        long dx = (long) toX - fromX;
        long dz = (long) toZ - fromZ;
        return Math.round(Math.sqrt((double) (dx * dx + dz * dz)));
    }
}
