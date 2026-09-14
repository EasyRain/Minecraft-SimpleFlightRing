package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The first step of the ocean relic ring's quest.
 * <p>
 * The broken ocean ring is found in shipwrecks. While it is still waiting (no
 * {@code guardian_acknowledged} component) killing an ELDER GUARDIAN while CARRYING it -
 * anywhere in the inventory, the offhand or the Curios slot - earns its acknowledgement: the
 * stack gains the component, starts to glint and its tooltip switches to the blessed lines
 * ("the sleeping guardian has accepted you" / "use the ocean's greatest treasure"). That is the
 * recipe gate for forging the working ring out of a heart of the sea, two prismarine shards and
 * a nautilus shell (see {@code ocean_flight_ring.json}).
 * <p>
 * One Elder Guardian acknowledges exactly ONE ring: if the player carries several waiting rings,
 * only the first one found is blessed, and the rest keep waiting for their own guardian.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class OceanRingQuest {

    /** Feedback when the ring earns the blessing. */
    private static final String GUARDIAN_ACKNOWLEDGED = "message.simpleflightring.ocean_guardian_acknowledged";

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().getType() != EntityType.ELDER_GUARDIAN) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack ring = findWaitingRing(player);
        if (ring.isEmpty()) {
            return;
        }

        ring.set(ModDataComponents.GUARDIAN_ACKNOWLEDGED.get(), Unit.INSTANCE);

        ServerLevel level = player.level();
        // The conduit's own particles and activation sound: the guardian acknowledges the ring.
        level.sendParticles(ParticleTypes.NAUTILUS,
                player.getX(), player.getY() + 0.8, player.getZ(),
                24, 0.5, 0.7, 0.5, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.CONDUIT_ACTIVATE,
                SoundSource.PLAYERS, 1.0F, 1.2F);
        player.sendOverlayMessage(
                Component.translatable(GUARDIAN_ACKNOWLEDGED).withStyle(ChatFormatting.GRAY));

        FlightRingMod.LOGGER.debug("[FlightRing] {} earned the sleeping guardian's acknowledgement for the ocean ring",
                player.getName().getString());
    }

    /**
     * The carried broken ocean ring that is still waiting, or an empty stack. Only this one
     * stack is blessed: one Elder Guardian = one ring, even when the player carries several.
     */
    private static ItemStack findWaitingRing(ServerPlayer player) {
        ItemStack worn = CuriosCompat.isLoaded() ? CuriosCompat.findRingInSlot(player) : ItemStack.EMPTY;
        if (isWaiting(worn)) {
            return worn;
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (isWaiting(stack)) {
                return stack;
            }
        }
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        return isWaiting(offhand) ? offhand : ItemStack.EMPTY;
    }

    private static boolean isWaiting(ItemStack stack) {
        return stack.getItem() instanceof DamagedRingItem damaged
                && damaged.relic() == RelicRing.OCEAN
                && !DamagedRingItem.isGuardianAcknowledged(stack);
    }

    private OceanRingQuest() {
    }
}
