package com.flightring.mixin;

import com.flightring.DesertSense;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

    /**
     * True while this handler is asking the game what the outline colour would be without it.
     * Only ever touched on the render thread, so a plain flag is enough.
     */
    @Unique
    private static boolean simpleflightring$readingOriginal;

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

    /**
     * HEAD rather than RETURN on purpose: plenty of mods colour outlines by injecting at RETURN
     * of this very method (Apotheosis paints a boss's outline from the colour of its name), and
     * an injection that runs after one of those is skipped as soon as the earlier one cancels the
     * callback. Answering before the vanilla body runs cannot be short-circuited by anybody.
     * <p>
     * <b>Every mod that has an opinion wins.</b> Before colouring anything the handler asks, with
     * a re-entrancy flag, what the colour would be without it. Plain white means nobody cares
     * about this entity - which is exactly the creatures the sense is for - so it takes over;
     * anything else (a name colour, a scoreboard team colour, whatever a mod computes) is left
     * exactly as it was found. No mod names are hard coded: this defers to all of them at once.
     */
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void simpleflightring$dangerSenseColor(CallbackInfoReturnable<Integer> cir) {
        if (simpleflightring$readingOriginal) {
            return;                          // our own question below: let the real body answer
        }
        Entity self = (Entity) (Object) this;
        if (self.level() == null || !self.level().isClientSide() || !DesertSense.senses(self)) {
            return;
        }
        int original;
        simpleflightring$readingOriginal = true;
        try {
            original = self.getTeamColor();
        } finally {
            simpleflightring$readingOriginal = false;
        }
        if (original != DesertSense.FRIENDLY_COLOR) {
            return;                          // somebody else colours this one: they win
        }
        cir.setReturnValue(DesertSense.colorFor(self));
    }
}
