package com.flightring;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FlightRingMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FLIGHT_RINGS =
            TABS.register("flight_rings", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.simpleflightring"))
                    .icon(() -> new ItemStack(ModItems.WOOD_FLIGHT_RING.get()))
                    .displayItems((parameters, output) -> {
                        for (var ring : ModItems.ALL) {
                            output.accept(new ItemStack(ring.get()));
                        }
                        // The broken relic rings, right after the working ones they repair into.
                        for (var damaged : ModItems.DAMAGED_RELIC_RINGS.values()) {
                            output.accept(new ItemStack(damaged.get()));
                        }
                        // The sculk quest's second step (already fed a Warden's soul), so the
                        // 8 echo shard forge recipe can be tried without hunting a Warden.
                        ItemStack fedSculkRing = new ItemStack(ModItems.DAMAGED_RELIC_RINGS.get(RelicRing.SCULK).get());
                        fedSculkRing.set(ModDataComponents.WARDEN_SOUL.get(), Unit.INSTANCE);
                        output.accept(fedSculkRing);
                        output.accept(new ItemStack(ModItems.INDESTRUCTIBLE_CORE.get()));
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
