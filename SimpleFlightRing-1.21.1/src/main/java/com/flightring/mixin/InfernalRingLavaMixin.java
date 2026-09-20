package com.flightring.mixin;

import com.flightring.InfernalRingQuest;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The infernal relic ring lives in lava instead of burning up in it
 * (see {@link InfernalRingQuest#tickInLava}): it sinks in the way an item thrown into water does,
 * floats back up and then rides the surface until it is picked up, and a charged broken ring is
 * quenched into the working ring on the way in.
 * <p>
 * This runs on both sides. The buoyancy is ordinary physics and the client has to simulate exactly
 * the same thing, or the ring would jitter against the server's position. The quench itself only
 * ever happens on the server.
 * <p>
 * {@code age} is reset while the ring is in the lava, so a ring thrown into lava can never expire
 * before its owner comes back for it - that is the one thing vanilla's own item-in-fluid
 * behaviour does not do.
 */
@Mixin(ItemEntity.class)
public abstract class InfernalRingLavaMixin {

    @Shadow
    private int age;

    @Inject(method = "tick", at = @At("TAIL"))
    private void simpleflightring$floatInLava(CallbackInfo ci) {
        if (InfernalRingQuest.tickInLava((ItemEntity) (Object) this)) {
            this.age = 0;
        }
    }
}
