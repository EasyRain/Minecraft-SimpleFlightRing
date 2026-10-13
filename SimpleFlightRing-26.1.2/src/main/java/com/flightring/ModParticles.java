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
 * There are two so far, {@link #SOUL_FLAME} and {@link #BROKEN_PORTAL}. They exist for the same
 * reason: vanilla particles carry their own motion and their own tint, and the mod wants a different
 * look. {@link #SOUL_FLAME} is the infernal ring's own soul fire (vanilla's flame look, none of its
 * drift); {@link #BROKEN_PORTAL} is the broken portal's wisp (vanilla's portal particle motion, in
 * light gray instead of vanilla's purple).
 * <p>
 * Both keep vanilla's textures: their particle descriptions reuse {@code minecraft:soul_fire_flame}
 * and {@code minecraft:generic_*}, so nothing new is drawn.
 */
public final class ModParticles {

    /** The particle registry of this mod. */
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, FlightRingMod.MODID);

    /** {@code simpleflightring:soul_flame} - see {@link ClientParticles} for its client side. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SOUL_FLAME =
            PARTICLES.register("soul_flame", () -> new SimpleParticleType(false));

    /** {@code simpleflightring:broken_portal} - see {@link ClientParticles} for its client side. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BROKEN_PORTAL =
            PARTICLES.register("broken_portal", () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}
