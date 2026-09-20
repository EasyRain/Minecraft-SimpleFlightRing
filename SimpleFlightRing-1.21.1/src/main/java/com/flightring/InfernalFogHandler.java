package com.flightring;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * "The flame lord looks straight through lava" - removes the lava fog for a wearer of the infernal
 * ring, exactly the way {@link OceanFogHandler} removes the water fog for the ocean ring. Client
 * side only: the fog is a purely visual thing, so the worn ring is read from the local player's
 * Curios slot and no packet is needed.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class InfernalFogHandler {

    /** Push the fog beyond anything the player can see: effectively "no lava fog". */
    private static final float NO_FOG_NEAR = 1.0E6F;
    private static final float NO_FOG_FAR = 2.0E6F;

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.LAVA) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || FlameLordPassives.wornRing(player).isEmpty()) {
            return;
        }
        event.setCanceled(true);
        event.setNearPlaneDistance(NO_FOG_NEAR);
        event.setFarPlaneDistance(NO_FOG_FAR);
    }

    private InfernalFogHandler() {
    }
}
