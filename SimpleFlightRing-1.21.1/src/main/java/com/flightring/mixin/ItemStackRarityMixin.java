package com.flightring.mixin;

import com.flightring.ModDataComponents;
import com.flightring.RingAffix;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reports a real vanilla rarity for rings that carry a random affix.
 * <p>
 * The affix stores only {@code {prefix, rarity}} - no separate {@code minecraft:rarity} component -
 * so without this every affixed ring would answer {@code COMMON} and mods that colour a tooltip
 * border or sort by rarity (Apotheosis, JEI/EMI, ...) would see nothing. {@link RingAffix.Rarity}
 * maps our five tiers onto the four vanilla ones (the two top tiers share {@code EPIC}).
 * <p>
 * Note that the <b>displayed</b> colours stay ours: {@code RingAffix#decorate} colours every name
 * part explicitly, and a parent style (the one {@code getHoverName()} adds) only fills in what the
 * parts leave open.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackRarityMixin {

    @Inject(method = "getRarity", at = @At("HEAD"), cancellable = true)
    private void flightring$affixRarity(CallbackInfoReturnable<Rarity> cir) {
        RingAffix affix = ((ItemStack) (Object) this).get(ModDataComponents.RING_AFFIX.get());
        if (affix != null) {
            cir.setReturnValue(affix.rarity().vanillaRarity());
        }
    }
}
