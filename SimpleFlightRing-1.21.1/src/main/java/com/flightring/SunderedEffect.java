package com.flightring;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * "破甲" (Sundered): the visible, clearable half of the 破甲 prefix.
 * <p>
 * The actual armour loss is a transient {@code ARMOR} modifier that accumulates across hits (see
 * {@link RingAffixHandler}); this effect is its <b>timer</b>. While the effect is on the target the shredded
 * armour stays off; once it runs out - or somebody removes the debuff (milk, another mod's "cleanse") - the
 * modifier is dropped and the armour comes back.
 * <p>
 * The removal is done in {@link #removeAttributeModifiers}, which vanilla calls for <b>every</b> entity the
 * effect ends on, so mobs recover exactly like players do - no per-entity bookkeeping needed.
 */
public class SunderedEffect extends MobEffect {

    /** Duration of one application: 30 seconds. */
    public static final int DURATION = 600;

    /** Id of the {@code ARMOR} modifier {@link RingAffixHandler} accumulates. */
    public static final net.minecraft.resources.ResourceLocation SHRED_ARMOUR_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FlightRingMod.MODID, "shred_armour");

    public SunderedEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B5A2B);
    }

    @Override
    public void removeAttributeModifiers(net.minecraft.world.entity.ai.attributes.AttributeMap attributes) {
        var instance = attributes.getInstance(Attributes.ARMOR);
        if (instance != null) {
            instance.removeModifier(SHRED_ARMOUR_ID);
        }
        super.removeAttributeModifiers(attributes);
    }
}
