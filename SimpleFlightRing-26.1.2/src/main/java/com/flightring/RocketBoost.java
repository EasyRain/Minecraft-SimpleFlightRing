package com.flightring;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * "Rocket Boost" enchantment behaviour.
 * <p>
 * While the player is gliding ({@link Player#isFallFlying()}) - with elytra or any
 * other gliding source such as armour-based flight from other mods - pressing the
 * jump key (space by default) fires a vanilla firework-rocket-style boost. The boost
 * lasts {@code level * 10} ticks (matching a firework rocket of that flight duration)
 * and costs {@code level * 10} seconds of flight time per use (the durability cost is
 * accumulated and applied server-side in {@link FlightHandler}).
 * <p>
 * The jump key is reused on purpose: the trigger piggybacks on the existing jump
 * binding, so jumping keeps working and no second key binding conflicts with it.
 * Any enchantment level is honoured as-is (mods that raise the cap are supported).
 */
public class RocketBoost {

    /** Remaining boost ticks; while positive the firework velocity kick is applied each tick. */
    private static int boostTicksRemaining = 0;

    private RocketBoost() {
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private static boolean isUsableRing(ItemStack stack) {
        return stack.getItem() instanceof FlightRingItem
                && (stack.has(ModDataComponents.INDESTRUCTIBLE.get()) || stack.getDamageValue() < stack.getMaxDamage());
    }

    private static int boostLevel(ItemStack stack, Holder<Enchantment> rocketBoost) {
        if (isUsableRing(stack)) {
            return stack.getEnchantments().getLevel(rocketBoost);
        }
        return 0;
    }

    /** Vanilla firework-rocket velocity kick (see {@code FireworkRocketEntity}). */
    private static void applyBoostVelocity(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 vel = player.getDeltaMovement();
        player.setDeltaMovement(
                vel.x + look.x * 0.1 + (look.x * 1.5 - vel.x) * 0.5,
                vel.y + look.y * 0.1 + (look.y * 1.5 - vel.y) * 0.5,
                vel.z + look.z * 0.1 + (look.z * 1.5 - vel.z) * 0.5);
    }

    private static Holder<Enchantment> rocketBoostHolder(Player player) {
        return player.registryAccess().holderOrThrow(ModEnchantments.ROCKET_BOOST);
    }

    // ------------------------------------------------------------------
    // Client
    // ------------------------------------------------------------------

    @EventBusSubscriber(modid = FlightRingMod.MODID, value = Dist.CLIENT)
    public static final class Client {

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;
            if (player == null || minecraft.level == null) {
                boostTicksRemaining = 0;
                return;
            }

            // Trigger on the jump key (space by default). Reusing the jump binding
            // means jumping is unaffected and no key conflict is introduced.
            if (minecraft.options.keyJump.consumeClick() && player.isFallFlying()) {
                int level = clientBoostLevel(player);
                if (level > 0) {
                    boostTicksRemaining = level * 10;
                    ClientPacketDistributor.sendToServer(new RocketBoostPayload(level));
                }
            }

            // Keep applying the boost every tick while it lasts and the player is still gliding.
            if (boostTicksRemaining > 0) {
                if (player.isFallFlying()) {
                    applyBoostVelocity(player);
                    boostTicksRemaining--;
                } else {
                    boostTicksRemaining = 0;
                }
            }
        }

        /** Highest rocket-boost level among the player's readable rings (Curios, inventory, offhand). */
        private static int clientBoostLevel(Player player) {
            Holder<Enchantment> rocketBoost = rocketBoostHolder(player);
            if (CuriosCompat.isLoaded()) {
                int level = boostLevel(CuriosCompat.findRingInSlot(player), rocketBoost);
                if (level > 0) {
                    return level;
                }
            }
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                int level = boostLevel(stack, rocketBoost);
                if (level > 0) {
                    return level;
                }
            }
            return boostLevel(player.getItemBySlot(EquipmentSlot.OFFHAND), rocketBoost);
        }
    }

    // ------------------------------------------------------------------
    // Server
    // ------------------------------------------------------------------

    // (The server-side durability cost lives in FlightHandler.applyRocketBoost,
    // which reuses the per-player state, ring lookup and backpack write-back.)
}

