package com.flightring;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network payload registration. Registered manually on the mod event bus by
 * {@link FlightRingMod}.
 */
public final class ModPayloads {

    private ModPayloads() {
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(FlightRingMod.MODID).versioned("1");
        registrar.playToClient(FlightTimePayload.TYPE, FlightTimePayload.STREAM_CODEC, ModPayloads::handleFlightTime);
        registrar.playToClient(RingEnergyPayload.TYPE, RingEnergyPayload.STREAM_CODEC, ModPayloads::handleRingEnergy);
        registrar.playToClient(DesertHostilesPayload.TYPE, DesertHostilesPayload.STREAM_CODEC,
                ModPayloads::handleDesertHostiles);
        registrar.playToServer(RocketBoostPayload.TYPE, RocketBoostPayload.STREAM_CODEC, ModPayloads::handleRocketBoost);
        registrar.playToServer(RingAbilityKeyPayload.TYPE, RingAbilityKeyPayload.STREAM_CODEC, ModPayloads::handleAbilityKey);
    }

    private static void handleFlightTime(FlightTimePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientFlightTime.update(payload.totalSeconds());
            FlightRingMod.LOGGER.debug("[FlightRing] received total flight time: {} s", payload.totalSeconds());
        });
    }

    private static void handleRingEnergy(RingEnergyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRingEnergy.update(payload.energy(), payload.maxEnergy()));
    }

    /** The creatures hunting this wearer; {@link DesertSense} outlines them red. */
    private static void handleDesertHostiles(DesertHostilesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DesertSense.setHostile(payload.entityIds()));
    }

    private static void handleRocketBoost(RocketBoostPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                FlightHandler.applyRocketBoost(serverPlayer);
            }
        });
    }

    private static void handleAbilityKey(RingAbilityKeyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                RingAbilityKeyHandler.onAbilityKey(serverPlayer, payload.slot());
            }
        });
    }
}
