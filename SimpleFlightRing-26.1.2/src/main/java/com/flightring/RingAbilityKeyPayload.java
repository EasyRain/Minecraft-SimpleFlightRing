package com.flightring;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server payload fired by the ring ability key (V by default).
 * <p>
 * {@code slot} is reserved for rings with more than one active ability: 0 means "the
 * ring's primary ability", which is all the placeholder needs today. The server
 * re-derives everything else from the ring the player is actually wearing, so a
 * malicious client cannot trigger anything it does not own.
 */
public record RingAbilityKeyPayload(int slot) implements CustomPacketPayload {

    public static final Type<RingAbilityKeyPayload> TYPE =
            new Type<>(Identifier.parse("simpleflightring:ability_key"));

    public static final StreamCodec<ByteBuf, RingAbilityKeyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            RingAbilityKeyPayload::slot,
            RingAbilityKeyPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
