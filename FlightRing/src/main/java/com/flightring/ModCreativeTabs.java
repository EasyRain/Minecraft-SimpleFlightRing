package com.flightring;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FlightRingMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FLIGHT_RINGS =
            TABS.register("flight_rings", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.flightring"))
                    .icon(() -> new ItemStack(ModItems.WOOD_FLIGHT_RING.get()))
                    .displayItems((parameters, output) -> {
                        for (var ring : ModItems.ALL) {
                            output.accept(new ItemStack(ring.get()));
                        }
                        output.accept(new ItemStack(ModItems.INDESTRUCTIBLE_CORE.get()));
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
