package com.flightring;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.List;

/**
 * The first step of the miner relic ring's quest.
 * <p>
 * The broken miner ring is found in abandoned mineshaft chests. Throwing it on the ground
 * next to a blast reforge it: when an explosion goes off around it the ring gains the
 * {@code blast_forged} component, its tooltip's third line switches from "it must be
 * reshaped by the power of a blast" to "only the missing material has to be added" and it
 * starts to glint. From there 8 iron ingots around it forge the working ring
 * ({@code miner_flight_ring.json}).
 * <p>
 * The ring survives the blast that reforges it: it is removed from the explosion's affected
 * entity list while the event is still being processed, so vanilla never damages (and
 * destroys) the item.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class MinerRingQuest {

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        List<Entity> affected = event.getAffectedEntities();
        for (Entity entity : List.copyOf(affected)) {
            if (!(entity instanceof ItemEntity item)) {
                continue;
            }
            ItemStack stack = item.getItem();
            if (!(stack.getItem() instanceof DamagedRingItem damaged)
                    || damaged.relic() != RelicRing.MINER
                    || DamagedRingItem.isBlastForged(stack)) {
                continue;
            }

            stack.set(ModDataComponents.BLAST_FORGED.get(), Unit.INSTANCE);
            affected.remove(entity);      // the ring must live through its own reforge

            if (entity.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT,
                        entity.getX(), entity.getY() + 0.2, entity.getZ(),
                        16, 0.25, 0.25, 0.25, 0.15);
                level.playSound(null, entity.blockPosition(), SoundEvents.ANVIL_USE,
                        SoundSource.BLOCKS, 1.0F, 1.4F);
            }
            FlightRingMod.LOGGER.debug("[FlightRing] a damaged miner ring was reforged by a blast at {}",
                    entity.blockPosition());
        }
    }

    private MinerRingQuest() {
    }
}
