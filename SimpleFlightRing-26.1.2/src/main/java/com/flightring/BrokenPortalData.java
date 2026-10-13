package com.flightring;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * The little bit of per-player bookkeeping the broken portal needs, stored in the entity's NeoForge
 * persistent data (so it travels with the player and survives restarts).
 * <p>
 * <b>No portal positions are remembered.</b> The portal pair is world shared, exactly like a nether
 * portal: which portal you arrive at follows from the coordinates (this dimension sits 1:1 next to
 * the overworld), so the same portal always leads to the same counterpart for every player. Three
 * things are kept, and all three are about the player, not about the link:
 * <ul>
 *   <li><b>originDimension</b> - which dimension the player stepped into the broken dimension from.
 *       Needed because a nether portal in the nether can be converted too, and the way back has to
 *       return there. Defaults to the overworld;</li>
 *   <li><b>hintShown</b> - the one-time chat hint has already been shown;</li>
 *   <li><b>knowsDimension</b> - the player has already rewritten a portal (or been to the broken
 *       dimension), so the hint is suppressed for them.</li>
 * </ul>
 */
public final class BrokenPortalData {

    private static final String ROOT = "simpleflightring_broken";
    private static final String ORIGIN_DIMENSION = "origin_dimension";
    private static final String HINT_SHOWN = "hint_shown";
    private static final String KNOWS_DIMENSION = "knows_dimension";

    private BrokenPortalData() {
    }

    private static CompoundTag root(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        // 26.1.2 returns Optional views here; the stored tag instance has to be created first so the
        // caller writes into the entity's data rather than into a copy.
        return data.getCompound(ROOT).orElseGet(() -> {
            CompoundTag tag = new CompoundTag();
            data.put(ROOT, tag);
            return tag;
        });
    }

    /** Dimension the player last stepped into a broken portal from, or {@code null} for "overworld". */
    public static Identifier originDimension(Entity entity) {
        return root(entity).getString(ORIGIN_DIMENSION).map(Identifier::tryParse).orElse(null);
    }

    public static void setOriginDimension(Entity entity, Identifier dimension) {
        root(entity).putString(ORIGIN_DIMENSION, dimension.toString());
    }

    /** True once the one-time chat hint has been shown to this player. */
    public static boolean hintShown(Entity entity) {
        return root(entity).getBoolean(HINT_SHOWN).orElse(false);
    }

    public static void setHintShown(Entity entity) {
        root(entity).putBoolean(HINT_SHOWN, true);
    }

    /**
     * True once the player already opened a rewritten portal or visited the broken dimension - the
     * hint is meant for players who have not found it yet.
     */
    public static boolean knowsDimension(Entity entity) {
        return root(entity).getBoolean(KNOWS_DIMENSION).orElse(false);
    }

    public static void setKnowsDimension(Entity entity) {
        root(entity).putBoolean(KNOWS_DIMENSION, true);
    }
}
