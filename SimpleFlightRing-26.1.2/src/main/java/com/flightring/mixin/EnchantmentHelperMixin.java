package com.flightring.mixin;

import com.flightring.FlightRingItem;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The special rings' built-in enchantments are "intrinsic": they live on the item
 * (a mod data component) instead of the vanilla enchantments component, so they
 * cannot be removed (grindstone, ...). This injects them into the vanilla
 * enchantment lookup, using whichever of the intrinsic and the real enchantment
 * level is higher - a ring with Rocket Boost I built in can still be enchanted
 * with Rocket Boost II and use II.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    @Inject(method = "getItemEnchantmentLevel", at = @At("RETURN"), cancellable = true)
    private static void simpleflightring$intrinsicEnchantment(Holder<Enchantment> enchantment, ItemInstance instance,
                                                              CallbackInfoReturnable<Integer> cir) {
        if (instance instanceof ItemStack stack && stack.getItem() instanceof FlightRingItem ring) {
            int intrinsic = ring.getIntrinsicEnchantLevel(stack, enchantment);
            if (intrinsic > cir.getReturnValue()) {
                cir.setReturnValue(intrinsic);
            }
        }
    }
}
