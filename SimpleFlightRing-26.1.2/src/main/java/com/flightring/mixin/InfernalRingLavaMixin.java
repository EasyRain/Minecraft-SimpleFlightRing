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
 * A ring waiting in lava must not expire, and the obvious way to do that - resetting {@code age}
 * every tick - is wrong: on 1.21.1 both the item's spin and its floating bob are derived from
 * {@code age} ({@code ItemEntity#getSpin} and {@code ItemEntityRenderer}), so freezing it freezes
 * the rotation and leaves nothing but the per-frame interpolation wobble, which reads as the ring
 * twitching in place. Pushing the lifespan ahead of the current age instead keeps it turning and
 * still means it never despawns; the lifespan is saved with the entity, so it survives a reload,
 * and it is only touched while the ring is actually in the lava.
 */
@Mixin(ItemEntity.class)
public abstract class InfernalRingLavaMixin {

    /** The vanilla item lifetime in ticks: how far ahead the lifespan is pushed, every tick. */
    private static final int LIFETIME = 6000;

    @Shadow
    private int age;

    /** Public on the target, but a mixin still has to declare every member it touches. */
    @Shadow
    public int lifespan;

    @Inject(method = "tick", at = @At("TAIL"))
    private void simpleflightring$floatInLava(CallbackInfo ci) {
        if (InfernalRingQuest.tickInLava((ItemEntity) (Object) this)) {
            this.lifespan = this.age + LIFETIME;
        }
    }
}
