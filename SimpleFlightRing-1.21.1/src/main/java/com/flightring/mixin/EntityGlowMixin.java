package com.flightring.mixin;

import com.flightring.DesertSense;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The desert ring's danger sense, seen from the renderer's side.
 * <p>
 * Vanilla decides whether to draw an entity's outline from {@code Minecraft#shouldEntityAppearGlowing}
 * (which asks {@link Entity#isCurrentlyGlowing()}) and takes the outline colour from
 * {@link Entity#getTeamColor()} - the same two calls in 1.21.1 and in 26.1.2's render-state based
 * renderer. Making just those two answer differently for the sensed creatures therefore outlines
 * them for this client only, without touching the creatures themselves: nothing is applied to
 * them server side, so no other player sees anything.
 * <p>
 * Both hooks are guarded by {@code level().isClientSide()}, so a dedicated server keeps vanilla
 * behaviour ({@code isCurrentlyGlowing} is also read server side, where it reflects the glowing
 * tag). {@link DesertSense} is a common class on purpose: the mixin must not reference any
 * client-only type.
 */
@Mixin(Entity.class)
public abstract class EntityGlowMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void simpleflightring$dangerSenseGlow(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            return;                          // already glowing (tag or vanilla effect)
        }
        Entity self = (Entity) (Object) this;
        if (self.level() != null && self.level().isClientSide() && DesertSense.senses(self)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
    private void simpleflightring$dangerSenseColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level() != null && self.level().isClientSide() && DesertSense.senses(self)) {
            cir.setReturnValue(DesertSense.colorOf(self));
        }
    }
}
