package com.flightring;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

@Mod(FlightRingMod.MODID)
public class FlightRingMod {

    public static final String MODID = "flightring";
    public static final Logger LOGGER = LoggerFactory.getLogger(FlightRingMod.class);

    public FlightRingMod(IEventBus modEventBus) {
        ModItems.ITEMS.register(modEventBus);
        ModRecipeSerializers.SERIALIZERS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        // Client config (HUD flight timer): config/flightring-client.toml
        ModList.get().getModContainerById(MODID).ifPresent(container ->
                container.registerConfig(ModConfig.Type.CLIENT, FlightRingConfig.SPEC));

        // Client-only: HUD flight timer layer (fires on the mod event bus).
        if (FMLLoader.getDist() == Dist.CLIENT) {
            modEventBus.addListener(RegisterGuiLayersEvent.class, FlightHud::onRegisterGuiLayers);
        }

        // Network payloads (server -> client flight time sync).
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, ModPayloads::onRegisterPayloadHandlers);

        modEventBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> {
            if (ModList.get().isLoaded("curios")) {
                CuriosCompat.register();
                LOGGER.info("[FlightRing] Curios API detected - registered flight ring curio slot");
            } else {
                LOGGER.info("[FlightRing] Curios API not detected - flight rings work from the inventory");
            }
        }));

        // Optional Cloth Config API: in-game config screen from the mod list.
        if (ModList.get().isLoaded("cloth_config")) {
            ModList.get().getModContainerById(MODID).ifPresent(container ->
                    container.registerExtensionPoint(IConfigScreenFactory.class,
                            (Supplier<IConfigScreenFactory>) () ->
                                    (modContainer, screen) -> ClothConfigCompat.createConfigScreen(screen)));
            LOGGER.info("[FlightRing] Cloth Config detected - in-game config screen available");
        }
    }
}
