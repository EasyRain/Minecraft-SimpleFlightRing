package com.flightring;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(FlightRingMod.MODID)
public class FlightRingMod {

    public static final String MODID = "simpleflightring";
    public static final Logger LOGGER = LoggerFactory.getLogger(FlightRingMod.class);

    public FlightRingMod(IEventBus modEventBus) {
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModMobEffects.EFFECTS.register(modEventBus);
        ModAttributes.ATTRIBUTES.register(modEventBus);
        ModParticles.PARTICLES.register(modEventBus);
        // M2: the broken dimension's biome source / chunk generator / snow feature are all codec-based
        // worldgen registries. Without these three the dimension JSON and the placed feature cannot resolve
        // "simpleflightring:island" / "simpleflightring:broken_snow" and the server dies on registry loading.
        ModWorldGen.BIOME_SOURCES.register(modEventBus);
        ModWorldGen.CHUNK_GENERATORS.register(modEventBus);
        ModWorldGen.FEATURES.register(modEventBus);
        ModRecipeSerializers.SERIALIZERS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        // Our dodge attribute has to be part of the player's attribute supplier to be applied.
        modEventBus.addListener(EntityAttributeModificationEvent.class, ModAttributes::onEntityAttributeModification);

        // Client config (HUD flight timer): config/simpleflightring-client.toml
        ModList.get().getModContainerById(MODID).ifPresent(container ->
                container.registerConfig(ModConfig.Type.CLIENT, FlightRingConfig.SPEC));

        // Common (server-side) gameplay config: config/simpleflightring-common.toml
        ModList.get().getModContainerById(MODID).ifPresent(container ->
                container.registerConfig(ModConfig.Type.COMMON, BrokenConfig.SPEC));

        // Client-only: HUD flight timer layer (fires on the mod event bus).
        if (FMLEnvironment.getDist().isClient()) {
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

        // /simpleflightring locate ... - pure grid arithmetic, no chunk loading (see BrokenLocateCommand).
        NeoForge.EVENT_BUS.addListener(BrokenLocateCommand::register);

        // Configs are all loaded by common setup, so this is the first safe point to read our COMMON values
        // (never from ModConfigEvent - see BrokenConfig#snapshotAndLog).
        modEventBus.addListener(FMLCommonSetupEvent.class, event -> BrokenConfig.snapshotAndLog());

        modEventBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> {
            if (ModList.get().isLoaded("curios")) {
                CuriosCompat.register();
                LOGGER.info("[FlightRing] Curios API detected - registered flight ring curio slot");
            } else {
                LOGGER.info("[FlightRing] Curios API not detected - flight rings work from the inventory");
            }
        }));

        // In-game config screen: NeoForge's own menu (ConfigurationScreen). It collects every config of the mod
        // - the HUD options and the broken dimension's options - and opens each of them into sections with
        // breadcrumbs, uses the spec comments as tooltips and offers undo/redo. It replaced the Cloth tab-bar
        // screen (2026-10-13): a tab bar is not a menu, and registering a second Cloth factory silently made
        // the HUD screen unreachable - and Cloth only ever knew the four HUD entries, not the 50+ dimension
        // ones BrokenConfig gained in M2.
        if (FMLEnvironment.getDist().isClient()) {
            // The client types live in ConfigScreens, never here: a lambda mentioning Screen would put a client
            // type into FlightRingMod's method descriptors, which RuntimeDistCleaner cannot strip on a server.
            ModList.get().getModContainerById(MODID).ifPresent(ConfigScreens::register);
        }
    }
}
