package com.flightring;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "破甲" (Sundered): the visible, clearable half of the 破甲 prefix.
 * <p>
 * The actual armour loss is a transient {@code ARMOR} modifier that accumulates across hits (see
 * {@link RingAffixHandler}); this effect is its <b>timer</b>. While the effect is on the target the shredded
 * armour stays off; once it runs out - or somebody removes the debuff (milk, another mod's "cleanse") - the
 * modifier is dropped and the armour comes back. That gives players a real recovery path, which a bare
 * attribute modifier never had.
 */
public class SunderedEffect extends MobEffect {

    /** Duration of one application, in ticks. */
    public static final int DURATION = 200;

    public SunderedEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B5A2B);
    }
}
