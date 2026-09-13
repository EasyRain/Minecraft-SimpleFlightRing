package com.flightring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The first step of the sculk relic ring's quest.
 * <p>
 * The broken sculk ring is found in Ancient City chests. While it is still "hungry"
 * (no {@code warden_soul} component) killing a Warden while CARRYING it - anywhere in the
 * inventory, the offhand or the Curios slot - feeds the ring that soul: the stack gains
 * the component, starts to glint and its tooltip changes from "the ring hungers for a
 * Warden's soul" to "the ring needs a new vessel", which is the recipe gate for forging
 * the working ring out of 8 echo shards (see {@code sculk_flight_ring.json}).
 * <p>
 * One Warden feeds exactly ONE ring: if the player carries several hungry rings, only the
 * first one found is charged, and the rest keep waiting for their own Warden.
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class SculkRingQuest {

    /** Feedback when the ring swallows the soul. */
    private static final String SOUL_ABSORBED = "message.simpleflightring.sculk_soul_absorbed";

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().getType() != EntityType.WARDEN) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack ring = findHungryRing(player);
        if (ring.isEmpty()) {
            return;
        }

        ring.set(ModDataComponents.WARDEN_SOUL.get(), Unit.INSTANCE);

        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                player.getX(), player.getY() + 0.8, player.getZ(),
                28, 0.45, 0.7, 0.45, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.sendOverlayMessage(
                Component.translatable(SOUL_ABSORBED).withStyle(ChatFormatting.GRAY));

        FlightRingMod.LOGGER.debug("[FlightRing] {} fed a Warden's soul to the sculk ring",
                player.getName().getString());
    }

    /**
     * The carried broken sculk ring that has not swallowed a soul yet, or an empty stack.
     * Only this one stack is charged: one Warden = one ring, even when the player carries
     * several hungry rings.
     */
    private static ItemStack findHungryRing(ServerPlayer player) {
        ItemStack worn = CuriosCompat.isLoaded() ? CuriosCompat.findRingInSlot(player) : ItemStack.EMPTY;
        if (isHungry(worn)) {
            return worn;
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (isHungry(stack)) {
                return stack;
            }
        }
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        return isHungry(offhand) ? offhand : ItemStack.EMPTY;
    }

    private static boolean isHungry(ItemStack stack) {
        return stack.getItem() instanceof DamagedRingItem damaged
                && damaged.relic() == RelicRing.SCULK
                && !DamagedRingItem.hasWardenSoul(stack);
    }

    private SculkRingQuest() {
    }
}
