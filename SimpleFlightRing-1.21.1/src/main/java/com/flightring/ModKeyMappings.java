package com.flightring;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The ring ability key: <b>V</b> by default, listed under "Misc" in the vanilla
 * controls screen.
 * <p>
 * It is a placeholder for the special rings' ACTIVE abilities. Pressing it sends
 * {@link RingAbilityKeyPayload} to the server, which looks up the ring the player is
 * wearing and decides what happens ({@link RingAbilityKeyHandler}) - today that is just
 * a report, because every ability implemented so far triggers on its own (passive).
 * Active abilities plug into that handler without touching this class.
 * <p>
 * The key mapping itself is registered on the mod event bus from {@code FlightRingMod}
 * (it is an {@code IModBusEvent}); this class only carries the client-bus tick handler.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
public final class ModKeyMappings {

    /** Trigger key for the special rings' abilities. */
    public static final KeyMapping ABILITY_KEY = new KeyMapping(
            "key.simpleflightring.ability",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            KeyMapping.CATEGORY_MISC);

    /** Mod-bus registration, called from the mod constructor on the client only. */
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ABILITY_KEY);
        // Let ability tooltips print the key the player actually bound.
        AbilityKeyHint.setKeyName(ABILITY_KEY::getTranslatedKeyMessage);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        // Drain every queued press; only forward them while actually in a world.
        while (ABILITY_KEY.consumeClick()) {
            if (player != null && minecraft.level != null) {
                PacketDistributor.sendToServer(new RingAbilityKeyPayload(0));
            }
        }
    }

    private ModKeyMappings() {
    }
}
