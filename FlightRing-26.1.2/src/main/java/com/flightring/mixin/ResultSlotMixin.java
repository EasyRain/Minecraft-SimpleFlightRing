package com.flightring.mixin;

import com.flightring.RingRepairRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Vanilla ResultSlot#onTake consumes exactly 1 item per grid slot and then
 * re-adds ("grows") whatever getRemainingItems returned back on top of the
 * slot, so a recipe that consumes several items from one stack would actually
 * duplicate the leftovers. For RingRepairRecipe we replace the whole loop:
 * each slot is simply set to the standard remaining list (ring -> empty,
 * material -> count - consumed), which correctly consumes stacked inputs.
 * Only the server actually consumes items; the client preview uses the
 * default reminder, so the server-side path is patched only.
 */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Shadow
    protected abstract void checkTakeAchievements(ItemStack carried);

    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
    private void flightring$onTake(Player player, ItemStack carried, CallbackInfo ci) {
        CraftingInput.Positioned positioned = this.craftSlots.asPositionedCraftInput();
        CraftingInput input = positioned.input();
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Optional<RecipeHolder<CraftingRecipe>> maybeRecipe =
                serverLevel.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, serverLevel);
        if (maybeRecipe.isEmpty() || !(maybeRecipe.get().value() instanceof RingRepairRecipe ringRepair)) {
            return;
        }

        this.checkTakeAchievements(carried);
        NonNullList<ItemStack> remaining = ringRepair.getRemainingItems(input);

        int left = positioned.left();
        int top = positioned.top();
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                int slot = x + left + (y + top) * this.craftSlots.getWidth();
                this.craftSlots.setItem(slot, remaining.get(x + y * input.width()));
            }
        }
        ci.cancel();
    }
}
