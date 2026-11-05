package com.flightring;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * What the anvil is about to do to a ring: the {@link ReforgeMode} the reforge stone asked for, plus the affix
 * the ring had before (so a directed stone can keep the half it is not rerolling).
 * <p>
 * The preview hides the affix - it only shows the plain ring carrying this marker - and the roll happens once
 * the player takes the result. Never survives a reforge.
 */
public record PendingReforge(ReforgeMode mode, @Nullable RingAffix kept) {

    public static final Codec<PendingReforge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ReforgeMode.CODEC.fieldOf("mode").forGetter(PendingReforge::mode),
            RingAffix.CODEC.optionalFieldOf("kept").forGetter(pending -> Optional.ofNullable(pending.kept()))
    ).apply(instance, (mode, kept) -> new PendingReforge(mode, kept.orElse(null))));

    public static final StreamCodec<ByteBuf, PendingReforge> STREAM_CODEC = StreamCodec.composite(
            ReforgeMode.STREAM_CODEC, PendingReforge::mode,
            ByteBufCodecs.optional(RingAffix.STREAM_CODEC), pending -> Optional.ofNullable(pending.kept()),
            (mode, kept) -> new PendingReforge(mode, kept.orElse(null)));
}
