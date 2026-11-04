package com.flightring.mixin;

import com.flightring.CuriosCompat;
import com.flightring.FlightRingItem;
import com.flightring.RebornHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plays the vanilla "totem flip" animation with the worn <b>ring</b> instead of a totem of undying.
 * <p>
 * {@link RebornHandler} sends the entity event {@link RebornHandler#REBIRTH_ANIMATION_EVENT} after a 重生 save;
 * this mixin intercepts that one event id (the vanilla totem uses 35 and is left completely alone, so 劫掠's own
 * totem keeps its normal animation) and shows the ring via {@code GameRenderer#displayItemActivation}.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientRebirthAnimationMixin {

    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void flightring$rebirthAnimation(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (packet.getEventId() != RebornHandler.REBIRTH_ANIMATION_EVENT) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameRenderer == null) {
            return;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(minecraft.player);
        com.flightring.FlightRingMod.LOGGER.info("[REBIRTH-ANIM] event={}, ring={}, isRingItem={}",
                packet.getEventId(), ring, ring.getItem() instanceof FlightRingItem);
        if (!(ring.getItem() instanceof FlightRingItem)) {
            return;
        }
        minecraft.gameRenderer.displayItemActivation(ring.copy());
        ci.cancel();
    }
}
