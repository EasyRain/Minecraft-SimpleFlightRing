package com.flightring;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /simpleflightring reforge [prefix] [rarity]} - puts a random affix on the ring in the main hand.
 * <p>
 * Meant for testing (and for us) so the long {@code /give ... [simpleflightring:ring_affix={...}]} syntax is
 * not needed; {@code /give} of course keeps working.
 * <ul>
 *   <li>{@code reforge} - rolls <b>both</b> the prefix and the rarity;</li>
 *   <li>{@code reforge <prefix>} - keeps that prefix and rolls the rarity;</li>
 *   <li>{@code reforge <prefix> <rarity>} - both fixed.</li>
 * </ul>
 * The rarity roll uses the anvil weights, so {@link RingAffix.Rarity#SACRED} is possible (chest drops stay
 * capped at {@code LEGENDARY}). Tab completion lists the prefix and rarity keys.
 */
public final class RingReforgeCommand {

    private static final SuggestionProvider<CommandSourceStack> PREFIX_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (RingAffix.Prefix prefix : RingAffix.Prefix.values()) {
            if (prefix.key().startsWith(remaining)) {
                builder.suggest(prefix.key());
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> RARITY_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (RingAffix.Rarity rarity : RingAffix.Rarity.values()) {
            if (rarity.key().startsWith(remaining)) {
                builder.suggest(rarity.key());
            }
        }
        return builder.buildFuture();
    };

    private RingReforgeCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("simpleflightring")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reforge")
                        .executes(ctx -> reforge(ctx, null, null))
                        .then(Commands.literal("clear")
                                .executes(RingReforgeCommand::clear))
                        // Fixing only one half needs the keyword forms: the positional ones below always
                        // take the prefix first, so they cannot express "keep the rarity, roll the prefix".
                        .then(Commands.literal("prefix")
                                .then(Commands.argument("prefixKey", StringArgumentType.word())
                                        .suggests(PREFIX_SUGGESTIONS)
                                        .executes(ctx -> reforge(ctx,
                                                StringArgumentType.getString(ctx, "prefixKey"), null))))
                        .then(Commands.literal("rarity")
                                .then(Commands.argument("rarityKey", StringArgumentType.word())
                                        .suggests(RARITY_SUGGESTIONS)
                                        .executes(ctx -> reforge(ctx, null,
                                                StringArgumentType.getString(ctx, "rarityKey")))))
                        .then(Commands.argument("prefix", StringArgumentType.word())
                                .suggests(PREFIX_SUGGESTIONS)
                                .executes(ctx -> reforge(ctx, StringArgumentType.getString(ctx, "prefix"), null))
                                .then(Commands.argument("rarity", StringArgumentType.word())
                                        .suggests(RARITY_SUGGESTIONS)
                                        .executes(ctx -> reforge(ctx,
                                                StringArgumentType.getString(ctx, "prefix"),
                                                StringArgumentType.getString(ctx, "rarity")))))));
    }

    /**
     * Applies an affix to the held ring. A {@code null} prefix and/or rarity is rolled; a non-null one has to
     * name a known key, otherwise the command fails with a message instead of silently rolling.
     */
    private static int reforge(CommandContext<CommandSourceStack> ctx, String prefixKey, String rarityKey) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("simpleflightring.command.reforge.no_player"));
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof FlightRingItem)) {
            source.sendFailure(Component.translatable("simpleflightring.command.reforge.not_a_ring"));
            return 0;
        }
        RingAffix.Prefix prefix = null;
        if (prefixKey != null) {
            prefix = RingAffix.Prefix.byKey(prefixKey);
            if (prefix == null) {
                String unknown = prefixKey;
                source.sendFailure(Component.translatable("simpleflightring.command.reforge.bad_prefix", unknown));
                return 0;
            }
        }
        RingAffix.Rarity rarity = null;
        if (rarityKey != null) {
            rarity = RingAffix.Rarity.byKey(rarityKey);
            if (rarity == null) {
                String unknown = rarityKey;
                source.sendFailure(Component.translatable("simpleflightring.command.reforge.bad_rarity", unknown));
                return 0;
            }
        }
        RingAffix rolled = RingAffix.roll(source.getLevel().getRandom(), prefix, rarity);
        stack.set(ModDataComponents.RING_AFFIX.get(), rolled);
        RingAffix applied = rolled;
        source.sendSuccess(() -> Component.translatable("simpleflightring.command.reforge.done", applied.displayName()), false);
        return 1;
    }

    /**
     * {@code reforge clear} - removes the affix from the held ring (the ring itself and everything else
     * on the stack stays untouched, so a plain ring comes out just like a crafted one).
     */
    private static int clear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("simpleflightring.command.reforge.no_player"));
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof FlightRingItem)) {
            source.sendFailure(Component.translatable("simpleflightring.command.reforge.not_a_ring"));
            return 0;
        }
        if (!stack.has(ModDataComponents.RING_AFFIX.get())) {
            source.sendFailure(Component.translatable("simpleflightring.command.reforge.no_affix"));
            return 0;
        }
        stack.remove(ModDataComponents.RING_AFFIX.get());
        source.sendSuccess(() -> Component.translatable("simpleflightring.command.reforge.cleared"), false);
        return 1;
    }
}
