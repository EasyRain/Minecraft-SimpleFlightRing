package com.flightring;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * "The ocean's favourite sees clearly underwater" - removes the water fog for a wearer of the
 * ocean ring. Client side only: the fog is a purely visual thing, so the worn ring is read
 * straight from the local player's Curios slot (no packets involved).
 * <p>
 * 1.21.1 note: {@code FogRenderer} pushes the fog start/end to the {@code RenderSystem}
 * <b>before</b> the event fires and NeoForge only writes the event's values back when the event
 * was cancelled ({@code ClientHooks#onFogRender} checks {@code isCanceled()} first). So this
 * handler must cancel <b>and</b> set both distances - cancelling alone would leave the water fog
 * untouched, and setting the distances alone would do nothing at all.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class OceanFogHandler {

    /** Push the fog beyond anything the player can see: effectively "no water fog". */
    private static final float NO_FOG_NEAR = 1.0E6F;
    private static final float NO_FOG_FAR = 2.0E6F;

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.WATER) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !wearsOceanRing(player)) {
            return;
        }
        event.setCanceled(true);
        event.setNearPlaneDistance(NO_FOG_NEAR);
        event.setFarPlaneDistance(NO_FOG_FAR);
    }

    /** True while the local player wears a usable ocean ring in the Curios slot. */
    private static boolean wearsOceanRing(LocalPlayer player) {
        if (!CuriosCompat.isLoaded()) {
            return false;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        return ring.getItem() instanceof FlightRingItem item
                && item.hasAbility(RingAbility.OCEAN_FAVORED)
                && item.isUsable(ring);
    }

    private OceanFogHandler() {
    }
}
