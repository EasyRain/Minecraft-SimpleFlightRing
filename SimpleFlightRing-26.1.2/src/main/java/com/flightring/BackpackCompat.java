package com.flightring;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Optional Sophisticated Backpacks integration: flight rings stored inside
 * sophisticated backpacks carried by the player also count for flight and the
 * HUD countdown (including nested backpacks, up to {@value #MAX_DEPTH} levels,
 * and backpacks worn in armor / offhand / Curios slots).
 * <p>
 * Durability changes made to a ring inside a backpack must be written back
 * through {@link #writeBack(BackpackRing)} so the backpack's item data is
 * updated; otherwise the change is lost whenever the backpack is reloaded
 * (e.g. when it moves between inventory slots).
 * <p>
 * This class must only be touched when Sophisticated Backpacks is actually
 * loaded (see {@link #isLoaded()}).
 */
public final class BackpackCompat {

    /** Maximum nesting depth when scanning backpacks inside backpacks. */
    private static final int MAX_DEPTH = 3;

    private BackpackCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("sophisticatedbackpacks");
    }

    /** Location of a ring inside a backpack. */
    public record BackpackRing(IBackpackWrapper wrapper, int slotIndex) {
        public ItemStack stack() {
            return wrapper.getInventoryHandler().copyToList().get(slotIndex);
        }
    }

    /** Returns the first usable ring inside the player's backpacks, or empty. */
    public static Optional<BackpackRing> findFirstUsableRing(Player player) {
        for (BackpackRing ring : findRingsInBackpacks(player)) {
            ItemStack stack = ring.stack();
            if (stack.getDamageValue() < stack.getMaxDamage()) {
                return Optional.of(ring);
            }
        }
        return Optional.empty();
    }

    /**
     * Collects every flight ring stored in the player's sophisticated backpacks:
     * backpacks in the main inventory, armor slots, offhand and Curios slots are
     * scanned, including nested backpacks.
     */
    public static List<BackpackRing> findRingsInBackpacks(Player player) {
        List<BackpackRing> rings = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            scanBackpack(stack, rings, 0);
        }
        // Sophisticated backpacks can also be worn in the armor (chest) slot.
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (slot.isArmor()) {
                scanBackpack(player.getItemBySlot(slot), rings, 0);
            }
        }
        scanBackpack(player.getItemBySlot(EquipmentSlot.OFFHAND), rings, 0);
        if (CuriosCompat.isLoaded()) {
            CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                var equipped = handler.getEquippedCurios();
                for (int i = 0; i < equipped.getSlots(); i++) {
                    scanBackpack(equipped.getStackInSlot(i), rings, 0);
                }
            });
        }
        return rings;
    }

    /**
     * Persists a modified ring back into its backpack's item data. This must be
     * called after changing {@link BackpackRing#stack()} (e.g. durability drain),
     * otherwise the change is lost when the backpack is reloaded.
     */
    public static void writeBack(BackpackRing ring) {
        InventoryHandler handler = ring.wrapper().getInventoryHandler();
        ItemStack modified = ring.stack();
        handler.set(ring.slotIndex(), ItemResource.of(modified), modified.getCount());
        handler.triggerOnChangeListeners(ring.slotIndex());
    }

    private static void scanBackpack(ItemStack stack, List<BackpackRing> rings, int depth) {
        if (depth > MAX_DEPTH || stack.isEmpty() || !(stack.getItem() instanceof BackpackItem)) {
            return;
        }
        IBackpackWrapper wrapper = BackpackWrapper.fromStack(stack);
        NonNullList<ItemStack> contents = wrapper.getInventoryHandler().copyToList();
        for (int i = 0; i < contents.size(); i++) {
            ItemStack inner = contents.get(i);
            if (inner.getItem() instanceof FlightRingItem) {
                rings.add(new BackpackRing(wrapper, i));
            } else {
                scanBackpack(inner, rings, depth + 1);
            }
        }
    }
}
