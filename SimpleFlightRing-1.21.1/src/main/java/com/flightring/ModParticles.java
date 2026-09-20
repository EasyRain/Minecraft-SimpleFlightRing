package com.flightring;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Particle types added by this mod, registered on the mod event bus from the
 * {@link FlightRingMod} constructor exactly like {@link ModMobEffects}.
 * <p>
 * There is one so far, {@link #SOUL_FLAME}: the infernal ring's own soul fire. It exists because
 * vanilla's flame particles carry their own motion - an effect's ambient particle is even spawned
 * with a velocity of (1, 1, 1) - and the mod wants flames that stay exactly where they were
 * spawned. The look is still vanilla's: the particle description reuses
 * {@code minecraft:soul_fire_flame} rather than shipping a texture of its own.
 */
public final class ModParticles {

    /** The particle registry of this mod. */
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, FlightRingMod.MODID);

    /** {@code simpleflightring:soul_flame} - see {@link ClientParticles} for its client side. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SOUL_FLAME =
            PARTICLES.register("soul_flame", () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}
