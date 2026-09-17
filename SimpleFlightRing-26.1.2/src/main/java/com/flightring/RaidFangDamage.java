package com.flightring;

/**
 * Implemented on {@code EvokerFangs} by {@code EvokerFangsDamageMixin}: carries the damage factor
 * of the ring that summoned the fang, so the raid ring's Energy Burst enchant raises the fang
 * damage the same way it raises the damage of the other relic rings' abilities.
 */
public interface RaidFangDamage {

    /** Sets the factor vanilla's flat fang damage is multiplied by. */
    void setDamageMultiplier(float multiplier);
}
