package com.flightring;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

@Mod(FlightRingMod.MODID)
public class FlightRingMod {

    public static final String MODID = "simpleflightring";
    public static final Logger LOGGER = LoggerFactory.getLogger(FlightRingMod.class);

    public FlightRingMod(IEventBus modEventBus) {
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModMobEffects.EFFECTS.register(modEventBus);
        ModAttributes.ATTRIBUTES.register(modEventBus);
        ModParticles.PARTICLES.register(modEventBus);
        ModRecipeSerializers.SERIALIZERS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        // Our dodge attribute has to be part of the player's attribute supplier to be applied.
        modEventBus.addListener(EntityAttributeModificationEvent.class, ModAttributes::onEntityAttributeModification);

        // Client config (HUD flight timer): config/simpleflightring-client.toml
        ModList.get().getModContainerById(MODID).ifPresent(container ->
                container.registerConfig(ModConfig.Type.CLIENT, FlightRingConfig.SPEC));

        // Client-only: HUD flight timer layer (fires on the mod event bus).
        if (FMLLoader.getDist() == Dist.CLIENT) {
            modEventBus.addListener(RegisterGuiLayersEvent.class, FlightHud::onRegisterGuiLayers);
            // Ring ability key (V by default); the ticking handler lives in ModKeyMappings.
            modEventBus.addListener(RegisterKeyMappingsEvent.class, ModKeyMappings::onRegisterKeyMappings);
            // The mod's own soul fire particle (see ModParticles).
            modEventBus.addListener(RegisterParticleProvidersEvent.class, ClientParticles::onRegisterParticleProviders);
        }

        // Network payloads (server -> client flight time sync).
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, ModPayloads::onRegisterPayloadHandlers);

        // Game bus: the eternal soul fire's recorded damage factors are per loaded world, so they
        // are dropped when the server shuts down.
        NeoForge.EVENT_BUS.addListener(EternalSoulFireEffect::onServerStopping);

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
