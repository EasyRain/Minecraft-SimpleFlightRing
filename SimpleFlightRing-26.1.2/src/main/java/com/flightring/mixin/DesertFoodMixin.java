package com.flightring.mixin;

import com.flightring.DesertRingAbility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Desert ring effect 3 - "half again as much out of every meal".
 * <p>
 * 26.1.2 moved eating into the data driven consumable system: {@code FoodProperties#onConsume} is
 * what hands the nutrition to {@code FoodData}, and it gets the eating entity, so the extra
 * helping is added right before vanilla applies the normal one.
 * {@code FoodData#eat(food, saturationModifier)} uses the same saturation formula vanilla uses
 * for the base amount, so the extra half carries the proportionate saturation too. The wearer is
 * looked up through {@link DesertRingAbility#isDesertWalker}.
 */
@Mixin(FoodProperties.class)
public abstract class DesertFoodMixin {

    @Inject(method = "onConsume", at = @At("HEAD"))
    private void simpleflightring$desertFoodBonus(Level level, LivingEntity user, ItemStack stack,
                                                  Consumable consumable, CallbackInfo ci) {
        if (!(user instanceof Player player) || !DesertRingAbility.isDesertWalker(player)) {
            return;
        }
        FoodProperties food = (FoodProperties) (Object) this;
        int bonus = (int) (food.nutrition() * DesertRingAbility.FOOD_BONUS);
        if (bonus > 0) {
            player.getFoodData().eat(bonus, food.saturation());
        }
    }
}
