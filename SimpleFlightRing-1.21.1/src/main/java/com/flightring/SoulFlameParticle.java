package com.flightring;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
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
 * Everything that makes vanilla's flame feel alive is kept: the quad shrinks as it burns out
 * ({@link #getQuadSize}) and the light ramps up the way vanilla's own flame does
 * ({@link #getLightColor}), so it fades in and out like a flame rather than a static sprite.
 * <p>
 * The texture is vanilla's own ({@code minecraft:soul_fire_flame}, see
 * {@code assets/simpleflightring/particles/soul_flame.json}), so nothing new is drawn.
 */
@OnlyIn(Dist.CLIENT)
public class SoulFlameParticle extends TextureSheetParticle {

    protected SoulFlameParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = 0.0D;
        this.zd = 0.0D;
        // The one and only movement: a slow rise, straight up.
        this.yd = 0.010D + this.random.nextDouble() * 0.010D;
        this.pickSprite(sprites);
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
        this.alpha = 1.0F - (float) this.age / (float) this.lifetime;
    }

    /** Vanilla's flame shrinks as it burns out, which is most of what makes it feel alive. */
    @Override
    public float getQuadSize(float partialTick) {
        float f = ((float) this.age + partialTick) / (float) this.lifetime;
        return this.quadSize * (1.0F - f * f * 0.5F);
    }

    /** Vanilla's flame brightens over its life; the same ramp, so a soul flame glows. */
    @Override
    public int getLightColor(float partialTick) {
        float f = Mth.clamp(((float) this.age + partialTick) / (float) this.lifetime, 0.0F, 1.0F);
        int i = super.getLightColor(partialTick);
        int j = i & 0xFF;
        int k = i >> 16 & 0xFF;
        j += (int) (f * 15.0F * 16.0F);
        if (j > 240) {
            j = 240;
        }
        return j | k << 16;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
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
