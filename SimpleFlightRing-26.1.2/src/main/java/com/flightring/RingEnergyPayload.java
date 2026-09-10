package com.flightring;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client payload carrying the energy pool of the ring worn in the Curios
 * slot, so the HUD bar does not depend on the item's component being synced.
 * {@code energy = -1} / {@code maxEnergy = 0} means no ability ring is worn.
 */
public record RingEnergyPayload(float energy, float maxEnergy) implements CustomPacketPayload {

    public static final Type<RingEnergyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FlightRingMod.MODID, "ring_energy"));

    public static final StreamCodec<ByteBuf, RingEnergyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT,
            RingEnergyPayload::energy,
            ByteBufCodecs.FLOAT,
            RingEnergyPayload::maxEnergy,
            RingEnergyPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
