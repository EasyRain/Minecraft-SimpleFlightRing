package com.flightring.mixin;

import com.flightring.OceanFavoredAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Elder Guardian's curse, seen from the receiving side.
 * <p>
 * The curse is not an attack: vanilla hands Mining Fatigue III to every player within 50 blocks
 * of an elder guardian through {@code MobEffectUtil}, no matter what the guardian is targeting,
 * and then sends {@code GUARDIAN_ELDER_EFFECT} to those players - which is what draws the elder
 * guardian particle and plays the curse sound on the client. The effect itself is already
 * refused server side ({@link OceanFavoredAbility#onEffectApplicable}), but that packet is sent
 * from the very list the effect was handed to, so it still arrived.
 * <p>
 * This mixin declines the packet itself for a wearer of the ocean ring: the server keeps sending
 * it (nothing about the guardian changes, and other players in range get their curse as usual),
 * the client simply does not act on it - no particle, no curse sound.
 */
@Mixin(ClientPacketListener.class)
public abstract class ElderGuardianCurseMixin {

    @Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true)
    private void simpleflightring$refuseElderGuardianCurse(ClientboundGameEventPacket packet, CallbackInfo ci) {
        if (packet.getEvent() == ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT
                && OceanFavoredAbility.isCurseProtected(Minecraft.getInstance().player)) {
            ci.cancel();
        }
    }
}
