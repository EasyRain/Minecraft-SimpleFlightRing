package com.flightring;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client payload carrying the server-authoritative total remaining
 * flight time (in seconds) of the player's rings, including rings stored inside
 * sophisticated backpacks (whose contents are not reliably readable client-side).
 */
public record FlightTimePayload(int totalSeconds) implements CustomPacketPayload {

    public static final Type<FlightTimePayload> TYPE = new Type<>(Identifier.parse("simpleflightring:flight_time"));

    public static final StreamCodec<ByteBuf, FlightTimePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            FlightTimePayload::totalSeconds,
            FlightTimePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
