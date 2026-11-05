package com.flightring;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /simpleflightring reforge ...} - affixes for the ring in the main hand, without the long
 * {@code /give ... [simpleflightring:ring_affix={...}]} syntax ({@code /give} keeps working).
 * <p>
 * Four subcommands, each with a fixed role:
 * <ul>
 *   <li>{@code reforge roll} - rolls <b>both</b> the prefix and the rarity;</li>
 *   <li>{@code reforge prefix [prefix]} - sets that prefix, or rolls the prefix when omitted. The rarity
 *       is <b>kept</b>; on a ring without an affix yet it is rolled too;</li>
 *   <li>{@code reforge rarity [rarity]} - the same the other way round: rarity set or rolled, prefix kept
 *       (rolled on a ring without an affix);</li>
 *   <li>{@code reforge clear} - removes the affix.</li>
 * </ul>
 * The rarity roll uses the anvil weights, so {@link RingAffix.Rarity#SACRED} is possible (chest drops stay
 * capped at {@code LEGENDARY}). Prefix and rarity keys have tab completion.
 */
public final class RingReforgeCommand {

    /** Which half of the affix the invocation is about; the other half is kept (or rolled if absent). */
    private enum Half {
        BOTH,
        PREFIX,
        RARITY
    }

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

    /** Root names: the full one stays, {@code /sfr} is the short alias (the same tree is registered twice). */
    private static final String[] ROOTS = {"simpleflightring", "sfr"};

    public static void register(RegisterCommandsEvent event) {
        for (String root : ROOTS) {
            registerRoot(event, root);
        }
    }

    private static void registerRoot(RegisterCommandsEvent event, String root) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(root)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reforge")
                        .then(Commands.literal("roll")
                                .executes(ctx -> reforge(ctx, Half.BOTH, null, null)))
                        .then(Commands.literal("prefix")
                                .executes(ctx -> reforge(ctx, Half.PREFIX, null, null))
                                .then(Commands.argument("prefixKey", StringArgumentType.word())
                                        .suggests(PREFIX_SUGGESTIONS)
                                        .executes(ctx -> reforge(ctx, Half.PREFIX,
                                                StringArgumentType.getString(ctx, "prefixKey"), null))))
                        .then(Commands.literal("rarity")
                                .executes(ctx -> reforge(ctx, Half.RARITY, null, null))
                                .then(Commands.argument("rarityKey", StringArgumentType.word())
                                        .suggests(RARITY_SUGGESTIONS)
                                        .executes(ctx -> reforge(ctx, Half.RARITY, null,
                                                StringArgumentType.getString(ctx, "rarityKey")))))
                        .then(Commands.literal("clear")
                                .executes(RingReforgeCommand::clear)))
                .then(Commands.literal("durability")
                        .then(Commands.literal("damage")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setDamage(ctx,
                                                IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("unbreakable")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setUnbreakable(ctx,
                                                BoolArgumentType.getBool(ctx, "value")))))
                        .then(Commands.literal("charge")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setCharge(ctx,
                                                IntegerArgumentType.getInteger(ctx, "value")))))));
    }

    /**
     * The ring in the main hand, or {@link ItemStack#EMPTY} after sending the matching failure message.
     * Shared by the durability subcommands so they fail exactly like {@code reforge} does.
     */
    private static ItemStack heldRing(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("simpleflightring.command.reforge.no_player"));
            return ItemStack.EMPTY;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof FlightRingItem)) {
            source.sendFailure(Component.translatable("simpleflightring.command.reforge.not_a_ring"));
            return ItemStack.EMPTY;
        }
        return stack;
    }

    /**
     * {@code durability damage <value>} - sets how much durability the held ring has lost, clamped to its
     * maximum ({@code 0} therefore repairs it, the maximum leaves it inert). Handy for testing the
     * 重生 durability split without punching a dummy fifty times.
     */
    private static int setDamage(CommandContext<CommandSourceStack> ctx, int value) {
        CommandSourceStack source = ctx.getSource();
        ItemStack stack = heldRing(source);
        if (stack.isEmpty()) {
            return 0;
        }
        int maximum = stack.getMaxDamage();
        int damage = Math.max(0, Math.min(value, maximum));
        stack.setDamageValue(damage);
        source.sendSuccess(() -> Component.translatable("simpleflightring.command.durability.damage_set",
                Integer.toString(damage), Integer.toString(maximum)), false);
        return 1;
    }

    /**
     * {@code durability unbreakable <true|false>} - toggles the Indestructible Core marker, i.e. whether the
     * ring has infinite durability. Turning it off does not reset the damage value, so a ring that was left
     * fully damaged becomes inert again right away.
     */
    private static int setUnbreakable(CommandContext<CommandSourceStack> ctx, boolean value) {
        CommandSourceStack source = ctx.getSource();
        ItemStack stack = heldRing(source);
        if (stack.isEmpty()) {
            return 0;
        }
        if (value) {
            stack.set(ModDataComponents.INDESTRUCTIBLE.get(), Unit.INSTANCE);
        } else {
            stack.remove(ModDataComponents.INDESTRUCTIBLE.get());
        }
        source.sendSuccess(() -> Component.translatable(value
                ? "simpleflightring.command.durability.unbreakable_on"
                : "simpleflightring.command.durability.unbreakable_off"), false);
        return 1;
    }

    /**
     * {@code durability charge <value>} - sets how many 重生 charges the held ring has left, in whole charges
     * ({@code 0} = none left, the maximum = full). Only indestructible rings carry that bar: on a ring paid for
     * with durability the same information already shows up as the green bar.
     */
    private static int setCharge(CommandContext<CommandSourceStack> ctx, int value) {
        CommandSourceStack source = ctx.getSource();
        ItemStack stack = heldRing(source);
        if (stack.isEmpty()) {
            return 0;
        }
        RingAffix affix = stack.get(ModDataComponents.RING_AFFIX.get());
        if (affix == null || affix.prefix() != RingAffix.Prefix.REBORN
                || !stack.has(ModDataComponents.INDESTRUCTIBLE.get())) {
            source.sendFailure(Component.translatable("simpleflightring.command.durability.no_charge"));
            return 0;
        }
        int maximum = affix.rarity().ordinal() + 1;
        int remaining = Math.max(0, Math.min(value, maximum));
        // The component stores the ticks still owed, not the charges left: one charge takes a full cooldown
        // to come back, so (maximum - remaining) charges of debt is exactly that many cooldowns.
        long owed = (long) (maximum - remaining) * RebornHandler.INDESTRUCTIBLE_COOLDOWN_TICKS;
        if (owed <= 0L) {
            stack.remove(ModDataComponents.REBIRTH_CHARGE.get());
        } else {
            stack.set(ModDataComponents.REBIRTH_CHARGE.get(), owed);
        }
        source.sendSuccess(() -> Component.translatable("simpleflightring.command.durability.charge_set",
                Integer.toString(remaining), Integer.toString(maximum)), false);
        return 1;
    }

    /**
     * Applies an affix to the held ring. A {@code null} prefix / rarity means "roll it"; for the
     * {@code PREFIX} and {@code RARITY} halves the other half is taken from the ring's current affix (and
     * rolled too when the ring has none yet). An explicit key has to name a known one, otherwise the
     * command fails with a message instead of silently rolling.
     */
    private static int reforge(CommandContext<CommandSourceStack> ctx, Half half, String prefixKey, String rarityKey) {
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
        RingAffix existing = stack.get(ModDataComponents.RING_AFFIX.get());
        if (existing != null) {
            // Keep the half this invocation is not about; a fixed key of course wins over it.
            if (half == Half.PREFIX && rarity == null) {
                rarity = existing.rarity();
            } else if (half == Half.RARITY && prefix == null) {
                prefix = existing.prefix();
            }
        }
        RingAffix rolled = RingAffix.roll(source.getLevel().getRandom(), prefix, rarity);
        stack.set(ModDataComponents.RING_AFFIX.get(), rolled);
        RingAffix applied = rolled;
        source.sendSuccess(() -> Component.translatable("simpleflightring.command.reforge.done", applied.displayName()), false);
        return 1;
    }

    /**
     * {@code reforge clear} - removes the affix from the held ring (the ring itself and everything else on
     * the stack stays untouched, so a plain ring comes out just like a crafted one).
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
