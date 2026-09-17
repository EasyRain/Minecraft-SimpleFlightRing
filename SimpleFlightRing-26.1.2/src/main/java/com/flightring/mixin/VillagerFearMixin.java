package com.flightring.mixin;

import com.flightring.CuriosCompat;
import com.flightring.FlightRingItem;
import com.flightring.RingAbility;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The raid ring's wearer counts as an illager to the villages, and villagers run from illagers.
 * <p>
 * Villagers are brain driven ({@code Activity.PANIC} on {@code MemoryModuleType.NEAREST_HOSTILE})
 * and have no avoidance goal of their own, so the fear is added by hand here: exactly the
 * avoidance a villager uses for a zombie (8 blocks, walk 0.5, sprint 1.0), narrowed to the
 * players who are actually wearing the raid ring. It is injected at the end of
 * {@code Mob#registerGoals}, which every mob calls once from its constructor, so each villager
 * gets the goal exactly once.
 */
@Mixin(Mob.class)
public abstract class VillagerFearMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void simpleflightring$fearTheRaidWearer(CallbackInfo ci) {
        if ((Object) this instanceof Villager villager) {
            villager.goalSelector.addGoal(1, new AvoidEntityGoal<>(villager, Player.class, 8.0F, 0.5, 1.0,
                    target -> target instanceof Player player && wearsRaidRing(player)));
        }
    }

    private static boolean wearsRaidRing(Player player) {
        if (!CuriosCompat.isLoaded()) {
            return false;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        return ring.getItem() instanceof FlightRingItem item
                && item.hasAbility(RingAbility.RAID_PLUNDER)
                && item.isUsable(ring);
    }
}
