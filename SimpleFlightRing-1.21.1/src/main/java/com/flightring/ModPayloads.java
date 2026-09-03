package com.flightring;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network payload registration. The handler is registered manually on the mod
 * event bus by {@link FlightRingMod} (the annotation-driven mod-bus subscriber
 * is deprecated in 1.21.1).
 */
public final class ModPayloads {

    private ModPayloads() {
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(FlightRingMod.MODID).versioned("1");
        registrar.playToClient(FlightTimePayload.TYPE, FlightTimePayload.STREAM_CODEC, ModPayloads::handleFlightTime);
        registrar.playToServer(RocketBoostPayload.TYPE, RocketBoostPayload.STREAM_CODEC, ModPayloads::handleRocketBoost);
    }

    private static void handleFlightTime(FlightTimePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientFlightTime.update(payload.totalSeconds());
            FlightRingMod.LOGGER.debug("[FlightRing] received total flight time: {} s", payload.totalSeconds());
        });
    }

    private static void handleRocketBoost(RocketBoostPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                FlightHandler.applyRocketBoost(serverPlayer);
            }
        });
    }
}
