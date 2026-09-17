package com.flightring.mixin;

import com.flightring.RaidFangDamage;
import net.minecraft.world.entity.projectile.EvokerFangs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Lets the raid ring's <b>Energy Burst</b> enchant raise the damage of the fangs that ring
 * summons. Vanilla {@code EvokerFangs#dealDamageTo} deals a flat 6 to whatever it catches, both
 * for a fang without an owner and for one cast by a mob, so the constant is scaled on the way in
 * and the factor itself rides on the fang (see {@link RaidFangDamage}).
 */
@Mixin(EvokerFangs.class)
public abstract class EvokerFangsDamageMixin implements RaidFangDamage {

    @Unique
    private float simpleflightring$damageMultiplier = 1.0F;

    @Override
    public void setDamageMultiplier(float multiplier) {
        this.simpleflightring$damageMultiplier = multiplier;
    }

    @ModifyConstant(method = "dealDamageTo", constant = @Constant(floatValue = 6.0F))
    private float simpleflightring$scaleFangDamage(float damage) {
        return damage * this.simpleflightring$damageMultiplier;
    }
}
