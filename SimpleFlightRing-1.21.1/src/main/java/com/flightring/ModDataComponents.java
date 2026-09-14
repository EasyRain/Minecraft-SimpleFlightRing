package com.flightring;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data components added by this mod. {@link #INDESTRUCTIBLE} marks a flight
 * ring forged with the Indestructible Core: it never loses durability, its
 * tooltip shows infinite flight time and the HUD countdown is hidden.
 * {@link #INTRINSIC_ENCHANTMENTS} holds the special rings' built-in enchantments:
 * they are kept outside the vanilla enchantments component, so they cannot be
 * removed (grindstone, ...) - see {@code EnchantmentHelperMixin}.
 * {@link #RING_ENERGY} is the energy pool of the linked rings (Magic Lining) and
 * is absent while that pool is full - see {@link RingEnergy}.
 */
public class ModDataComponents {

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FlightRingMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> INDESTRUCTIBLE =
            COMPONENTS.registerComponentType("indestructible", builder -> builder
                    .persistent(Unit.CODEC)
                    .networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<IntrinsicEnchants>> INTRINSIC_ENCHANTMENTS =
            COMPONENTS.registerComponentType("intrinsic_enchantments", builder -> builder
                    .persistent(IntrinsicEnchants.CODEC)
                    .networkSynchronized(IntrinsicEnchants.STREAM_CODEC));

    /** Remaining energy of a linked ring; the component is absent while the pool is full. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> RING_ENERGY =
            COMPONENTS.registerComponentType("ring_energy", builder -> builder
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT));

    /**
     * Sculk relic ring quest step: the damaged sculk ring has absorbed a Warden's soul,
     * so it glints and can finally be forged into the working ring (see
     * {@link SculkRingQuest} and the {@code sculk_flight_ring} recipe). Absent means the
     * ring is still hungry, i.e. the first step of the quest.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> WARDEN_SOUL =
            COMPONENTS.registerComponentType("warden_soul", builder -> builder
                    .persistent(Unit.CODEC)
                    .networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    /**
     * Miner relic ring quest step: the damaged miner ring was thrown into a blast and got
     * reforged by it (see {@link MinerRingQuest}), so only the missing material has to be
     * added now (8 iron ingots, the {@code miner_flight_ring} recipe). Absent means the ring
     * is still waiting for its explosion.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> BLAST_FORGED =
            COMPONENTS.registerComponentType("blast_forged", builder -> builder
                    .persistent(Unit.CODEC)
                    .networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    /**
     * Emerald relic ring quest step: the damaged emerald ring was carried through a won raid
     * (see {@link EmeraldRingQuest}), so it remembers the old hero's light and only the new
     * vessel is missing (8 emeralds around it, the {@code emerald_flight_ring} recipe).
     * Absent means the ring is still asleep.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> HERO_CHARGED =
            COMPONENTS.registerComponentType("hero_charged", builder -> builder
                    .persistent(Unit.CODEC)
                    .networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    /**
     * Hero of the Village strength of an emerald ring: 1..5, exactly the level of the raid the
     * ring was carried through (the same 1..5 an ominous bottle / bad omen gives). It lives on
     * the damaged ring while it waits for its vessel and on the working ring afterwards, where
     * {@link EmeraldHeroAbility} turns it into the buff's amplifier. Absent means level 1.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> HERO_LEVEL =
            COMPONENTS.registerComponentType("hero_level", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * Ocean relic ring quest step: the damaged ocean ring was carried while an Elder Guardian
     * was slain (see {@link OceanRingQuest}), so it earned the tide's blessing and only the new
     * vessel is missing (a heart of the sea, prismarine shards and a nautilus shell - see the
     * {@code ocean_flight_ring} recipe). Absent means the ring is still waiting for the deep.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> TIDE_BLESSED =
            COMPONENTS.registerComponentType("tide_blessed", builder -> builder
                    .persistent(Unit.CODEC)
                    .networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    private ModDataComponents() {
    }
}
