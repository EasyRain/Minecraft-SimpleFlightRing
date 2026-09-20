package com.flightring;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Passives of the infernal relic ring, the "Flame Lord". Like every other relic ability they only
 * work while the ring sits in the Curios slot with durability left.
 * <ol>
 *   <li><b>Never burns</b>: permanent Fire Resistance, refreshed every tick with a short duration
 *       so the buff disappears together with the ring.</li>
 *   <li><b>Wither cannot touch the wearer</b>: refused in {@link MobEffectEvent.Applicable},
 *       whatever the source, and a Wither that is already on them is cured the moment the ring
 *       goes on.</li>
 *   <li><b>Lava is as clear as air</b>: pure client side, see {@link InfernalFogHandler}.</li>
 * </ol>
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class FlameLordPassives {

    /** Refreshed every tick, short enough to die with the ring rather than linger. */
    private static final int BUFF_DURATION_TICKS = 40;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || wornRing(player).isEmpty()) {
            return;
        }
        // ambient (no particles), showIcon: the player should see that it is on.
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, BUFF_DURATION_TICKS, 0, true, false, true));
        if (player.hasEffect(MobEffects.WITHER)) {
            player.removeEffect(MobEffects.WITHER);
        }
    }

    /** Wither never lands on the wearer, no matter who or what tries to apply it. */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        Holder<net.minecraft.world.effect.MobEffect> effect = event.getEffectInstance().getEffect();
        if (effect.is(MobEffects.WITHER)
                && event.getEntity() instanceof Player player
                && !wornRing(player).isEmpty()) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /**
     * The worn infernal ring, or an empty stack: needs the ability and durability left. Public
     * because the client side fire mixins read it for the local player.
     */
    public static ItemStack wornRing(Player player) {
        if (!CuriosCompat.isLoaded()) {
            return ItemStack.EMPTY;
        }
        ItemStack ring = CuriosCompat.findRingInSlot(player);
        if (ring.getItem() instanceof FlightRingItem item
                && item.hasAbility(RingAbility.FLAME_LORD)
                && item.isUsable(ring)) {
            return ring;
        }
        return ItemStack.EMPTY;
    }

    private FlameLordPassives() {
    }
}
