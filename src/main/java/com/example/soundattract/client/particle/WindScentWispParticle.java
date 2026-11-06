package com.example.soundattract.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;


public final class WindScentWispParticle extends SingleQuadParticle {
    private static final int LIFETIME_TICKS = 26;
    private final SpriteSet sprites;

    private WindScentWispParticle(ClientLevel level, double x, double y, double z,
                                  double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, null);
        this.sprites = sprites;
        this.setLifetime(LIFETIME_TICKS);
        this.gravity = 0.0F;
        this.hasPhysics = true;
        this.friction = 0.96F;
        this.quadSize = 0.14F;
        this.setColor(0.34F, 0.92F, 0.84F);
        this.setAlpha(0.78F);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float remaining = 1.0F - (float) this.age / this.lifetime;
        this.setAlpha(0.78F * Math.max(0.0F, remaining));
        this.quadSize = 0.14F * (0.65F + 0.35F * remaining);
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed,
                                       RandomSource random) {
            return new WindScentWispParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
