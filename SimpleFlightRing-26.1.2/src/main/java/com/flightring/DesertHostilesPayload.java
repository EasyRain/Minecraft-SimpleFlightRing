package com.flightring;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Server -> client payload carrying the entity ids of the creatures that currently hold a grudge
 * against the ring wearer (see {@link DesertHostileSync}).
 * <p>
 * The danger sense outlines the hostile creatures red; a neutral one that was attacked and is now
 * hunting the wearer (a wolf, an iron golem, a polar bear...) is only recognisable server side -
 * a mob's attack target and its persistent anger are not synced to clients - so the server says
 * so here. An empty list means "nothing wants you dead", and is sent when the ring comes off so
 * the client forgets the old grudges.
 */
public record DesertHostilesPayload(List<Integer> entityIds) implements CustomPacketPayload {

    public static final Type<DesertHostilesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "desert_hostiles"));

    public static final StreamCodec<ByteBuf, DesertHostilesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
            DesertHostilesPayload::entityIds,
            DesertHostilesPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
