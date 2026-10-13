package com.flightring;

import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Client side wiring for {@link ModParticles}: tells the particle engine which provider draws
 * {@code simpleflightring:soul_flame} and {@code simpleflightring:broken_portal}. The sprites come
 * from the particle descriptions ({@code assets/simpleflightring/particles/*.json}), which point at
 * vanilla's own textures. Registered from {@link FlightRingMod} inside its client-only block, so the
 * class is never loaded on a dedicated server.
 */
public final class ClientParticles {

    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SOUL_FLAME.get(), SoulFlameParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BROKEN_PORTAL.get(), BrokenPortalParticle.Provider::new);
    }

    private ClientParticles() {
    }
}
