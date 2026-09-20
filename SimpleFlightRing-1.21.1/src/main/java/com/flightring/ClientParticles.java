package com.flightring;

import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Client side wiring for {@link ModParticles}: tells the particle engine which provider draws
 * {@code simpleflightring:soul_flame}. The sprites come from the particle description
 * ({@code assets/simpleflightring/particles/soul_flame.json}), which points at vanilla's own soul
 * fire texture. Registered from {@link FlightRingMod} inside its client-only block, so the class
 * is never loaded on a dedicated server.
 */
public final class ClientParticles {

    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SOUL_FLAME.get(), SoulFlameParticle.Provider::new);
    }

    private ClientParticles() {
    }
}
