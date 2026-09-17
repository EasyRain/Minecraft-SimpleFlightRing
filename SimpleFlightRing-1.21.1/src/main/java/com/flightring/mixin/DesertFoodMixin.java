package com.flightring.mixin;

import com.flightring.DesertRingAbility;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Desert ring effect 3 - "half again as much out of every meal".
 * <p>
 * 1.21.1 applies a food's nutrition in {@code Player#eat}, which is the only place that knows both
 * the food and the eating player, so the extra helping is added right before vanilla applies the
 * normal one. {@code FoodData#eat(food, saturationModifier)} uses the same saturation formula
 * vanilla uses for the base amount, so the extra half carries the proportionate saturation too.
 * The wearer is looked up through {@link DesertRingAbility#isDesertWalker}.
 */
@Mixin(Player.class)
public abstract class DesertFoodMixin {

    @Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"))
    private void simpleflightring$desertFoodBonus(Level level, ItemStack stack, FoodProperties food,
                                                   CallbackInfoReturnable<ItemStack> cir) {
        if (food == null) {
            return;
        }
        Player player = (Player) (Object) this;
        if (!DesertRingAbility.isDesertWalker(player)) {
            return;
        }
        int bonus = (int) (food.nutrition() * DesertRingAbility.FOOD_BONUS);
        if (bonus > 0) {
            player.getFoodData().eat(bonus, food.saturation());
        }
    }
}
