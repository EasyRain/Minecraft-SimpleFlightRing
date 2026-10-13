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
 * The broken portal's own wisp: vanilla's portal particle, in light gray instead of purple.
 * <p>
 * Why a particle of its own is needed at all: vanilla's {@code PortalParticle} puts the purple in
 * the <b>code</b> ({@code rCol = f * 0.9, gCol = f * 0.3, bCol = f}) and draws the plain
 * {@code minecraft:generic_*} sprites, so the colour cannot be changed from the outside. Substituting
 * a vanilla gray particle does not work either - {@code WHITE_SMOKE} rises and spreads, which made
 * the portal look like it was spitting particles outwards instead of pulling them in.
 * <p>
 * The motion here is vanilla's portal motion, copied on purpose: the wisp eases from its spawn point
 * towards its small spawn velocity ({@link #tick}), so it hovers around the portal rather than
 * flying away, and it shrinks ({@link #getQuadSize}) and fades over its 40-50 tick life. The only
 * difference from vanilla is the tint: a light gray that is never darker than 65%.
 * <p>
 * The texture is vanilla's own generic particle sheet, see
 * {@code assets/simpleflightring/particles/broken_portal.json}.
 */
@OnlyIn(Dist.CLIENT)
public class BrokenPortalParticle extends TextureSheetParticle {

    private final double xStart;
    private final double yStart;
    private final double zStart;

    protected BrokenPortalParticle(ClientLevel level, double x, double y, double z,
                                   double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.xStart = x;
        this.yStart = y;
        this.zStart = z;
        this.quadSize = 0.1F * (this.random.nextFloat() * 0.2F + 0.5F);
        // Light gray instead of vanilla's purple: same brightness variation, no hue.
        float brightness = this.random.nextFloat() * 0.35F + 0.65F;
        this.rCol = brightness;
        this.gCol = brightness;
        this.bCol = brightness;
        this.lifetime = (int) (this.random.nextFloat() * 10.0F) + 40;
        this.pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public void move(double xa, double ya, double za) {
        this.setBoundingBox(this.getBoundingBox().move(xa, ya, za));
        this.setLocationFromBoundingbox();
    }

    @Override
    public float getQuadSize(float partialTick) {
        float f = ((float) this.age + partialTick) / (float) this.lifetime;
        f = 1.0F - f;
        f *= f;
        f = 1.0F - f;
        return this.quadSize * f;
    }

    @Override
    public int getLightColor(float partialTick) {
        float f = (float) this.age / (float) this.lifetime;
        f *= f;
        f *= f;
        int i = super.getLightColor(partialTick);
        int j = i & 0xFF;
        int k = i >> 16 & 0xFF;
        k += (int) (f * 15.0F * 16.0F);
        if (k > 240) {
            k = 240;
        }
        return j | k << 16;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            float f = (float) this.age / (float) this.lifetime;
            float f1 = -f + f * f * 2.0F;
            float f2 = 1.0F - f1;
            this.x = this.xStart + this.xd * (double) f2;
            // The trailing term is vanilla's: the wisp also drifts slightly upwards as it fades.
            this.y = this.yStart + this.yd * (double) f2 + (double) (1.0F - f);
            this.z = this.zStart + this.zd * (double) f2;
            this.setPos(this.x, this.y, this.z);
        }
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
            return new BrokenPortalParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
