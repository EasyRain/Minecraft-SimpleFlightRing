package com.flightring;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The mod's own soul fire: vanilla's soul fire flame look, none of vanilla's motion.
 * <p>
 * The reason this exists at all: vanilla flame particles drift. {@code FlameParticle} extends
 * {@code RisingParticle}, which multiplies the horizontal speed by 1.1 every tick the particle
 * does not move vertically, and vanilla spawns an effect's ambient particle with a velocity of
 * (1, 1, 1) on every axis - so a soul fire flame wanders diagonally for tens of blocks. This
 * particle rises straight up and never sideways: the only velocity it ever has is the small
 * upward one from its constructor, so it always stays on the creature it was spawned for.
 * <p>
 * The texture is vanilla's own ({@code minecraft:soul_fire_flame}, see
 * {@code assets/simpleflightring/particles/soul_flame.json}), so nothing new is drawn.
 */
@OnlyIn(Dist.CLIENT)
public class SoulFlameParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected SoulFlameParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.lifetime = 14 + this.random.nextInt(10);
        this.quadSize = 0.14F + this.random.nextFloat() * 0.06F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = 0.0D;
        this.zd = 0.0D;
        this.yd = 0.012D + this.random.nextDouble() * 0.010D;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        // Straight up, never sideways, and the rise slows down as it fades.
        this.yd *= 0.93D;
        this.move(0.0D, this.yd, 0.0D);
        this.setSpriteFromAge(this.sprites);
        this.alpha = 1.0F - (float) this.age / (float) this.lifetime;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Handed to {@code RegisterParticleProvidersEvent} by {@link ClientParticles}. */
    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new SoulFlameParticle(level, x, y, z, this.sprites);
        }
    }
}
