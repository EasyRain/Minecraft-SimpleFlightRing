package com.flightring;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server payload fired when the player triggers the Rocket Boost
 * enchantment (jump key while gliding). Carries the boost level the client
 * determined; the server re-derives the level from the active ring for the
 * durability cost, so the value is informational only.
 */
public record RocketBoostPayload(int level) implements CustomPacketPayload {

    public static final Type<RocketBoostPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "rocket_boost"));

    public static final StreamCodec<ByteBuf, RocketBoostPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            RocketBoostPayload::level,
            RocketBoostPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
