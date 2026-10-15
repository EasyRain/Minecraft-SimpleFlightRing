package com.flightring;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side gameplay configuration for the broken dimension (config/simpleflightring-common.toml).
 * <p>
 * Two layers on purpose, as the user asked for (2026-10-11): a blunt switch for the common case
 * ({@code allowModBiomes}) and fine-grained lists to fall back on - whole namespaces, single biomes and id
 * keywords - instead of having to throw every modded biome out at once.
 * <p>
 * The values are copied into the plain static fields the worldgen already reads ({@link #snapshot()}), so
 * the hot paths (one call per block column) keep reading a field instead of parsing config.
 */
@SuppressWarnings("unused")
public final class BrokenConfig {

    public static final ModConfigSpec SPEC;

    // islands -----------------------------------------------------------------
    private static final ModConfigSpec.ConfigValue<Integer> MIN_RADIUS;
    private static final ModConfigSpec.IntValue MAX_RADIUS;
    private static final ModConfigSpec.IntValue GAP_MIN;
    private static final ModConfigSpec.IntValue GAP_MAX;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> HEIGHT_LAYERS;
    private static final ModConfigSpec.DoubleValue MAX_STRETCH;

    // caves / surface ---------------------------------------------------------
    private static final ModConfigSpec.ConfigValue<Integer> WET_ISLAND_CHANCE;
    private static final ModConfigSpec.ConfigValue<Integer> CARVER_WATER_TABLE;
    private static final ModConfigSpec.BooleanValue FORCE_ALL_DRY;
    private static final ModConfigSpec.ConfigValue<Integer> BOWL_SHELL_MIN;
    private static final ModConfigSpec.DoubleValue REPAIR_RIM_FROM;
    private static final ModConfigSpec.ConfigValue<Integer> TOP_MARGIN;
    private static final ModConfigSpec.ConfigValue<Integer> BOTTOM_MARGIN;
    private static final ModConfigSpec.DoubleValue THICKNESS_SCALE;

    // structures --------------------------------------------------------------
    private static final ModConfigSpec.ConfigValue<Integer> STRUCTURE_UNDERSIDE_MARGIN;
    private static final ModConfigSpec.ConfigValue<Integer> STRUCTURE_RIM_MARGIN;
    private static final ModConfigSpec.ConfigValue<Integer> STRUCTURE_HEADROOM;

    // biome pool --------------------------------------------------------------
    private static final ModConfigSpec.BooleanValue ALLOW_MOD_BIOMES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> NAMESPACE_BLACKLIST;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_BLACKLIST;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_ALLOWLIST;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> FUNCTIONAL_KEYWORDS;
    private static final ModConfigSpec.BooleanValue EXCLUDE_FEATURELESS_BIOMES;
    private static final ModConfigSpec.BooleanValue EXCLUDE_FUNCTIONAL_BIOMES;
    private static final ModConfigSpec.BooleanValue LOG_POOL;
    private static final ModConfigSpec.BooleanValue LOG_LAYOUT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Island size and spacing / 岛屿大小与间距").push("islands");
        MIN_RADIUS = builder.comment("Smallest island radius (blocks). / 最小岛半径（格）。", "Default: 80")
                .define("minRadius", 80, o -> o instanceof Integer v && v >= 48 && v <= 256);
        MAX_RADIUS = builder.comment("Largest island radius (blocks). / 最大岛半径（格）。",
                        "The grid cell size is derived from this, and the minimum gap is guaranteed against it.",
                        "格边长按这个值推算；最小间距的几何保证也是按它算的。",
                        "320 is already the practical ceiling: an island's body grows with its radius, and past",
                        "that the bottom hits the world floor and gets cut flat (radius 320 on height layer 96",
                        "already flattens slightly) - beyond it, it stops reading as an island.",
                        "320 已是实际上限：岛体会随半径变厚，再大底部就会触到世界底而被削平",
                        "（半径 320 + 高度层 96 已经会轻微削平），再大就不像岛了。", "Default: 320")
                .defineInRange("maxRadius", 320, 64, 320);
        GAP_MIN = builder.comment("Minimum gap between two islands (blocks). / 岛间最小间距（格）。", "Default: 256")
                .defineInRange("gapMin", 256, 64, 768);
        GAP_MAX = builder.comment("Maximum gap between two islands (blocks). / 岛间最大间距（格）。", "Default: 512")
                .defineInRange("gapMax", 512, 64, 1024);
        HEIGHT_LAYERS = builder.comment("Y levels islands are centred on / 岛屿中心所在的高度层。",
                        "The default stays low on purpose: islands need headroom below the build limit (their top",
                        "reaches centre + ~14 blocks; the code additionally clamps it 8 blocks under the limit, so",
                        "nothing can be written out of bounds). Higher layers are allowed up to 256, and going",
                        "past that is the config's own problem.",
                        "默认值刻意压低：岛顶会到 中心+约14 格，需要给建筑上限留余量（代码另外会把顶部钳在",
                        "上限下方 8 格，保证不会写出界）。允许调到 256；再往上就是配置自己的问题了。",
                        "Default: [96, 112, 128]")
                .defineList("heightLayers", List.of(96, 112, 128), o -> o instanceof Integer);
        MAX_STRETCH = builder.comment("How long an island may get along its major axis / 椭圆最大长宽比。",
                        "Default: 2.2")
                .defineInRange("maxStretch", 2.2, 1.0, 3.0);
        builder.pop();

        builder.comment("Caves and surface / 矿洞与地表").push("caves");
        WET_ISLAND_CHANCE = builder.comment("One in N islands keeps vanilla's water in its caves (water-filled island);",
                        "every other island is carved dry. 1 = every island, 100 = 1 in 100.",
                        "每 N 座岛里有一座保留原版含水洞穴（水淹岛），其余为干洞。1 = 全部含水，100 = 百分之一。",
                        "Default: 10 (= 10%)")
                .define("wetIslandChance", 10, o -> o instanceof Integer v && v >= 1 && v <= 100);
        CARVER_WATER_TABLE = builder.comment("Water table used while carving dry islands (lava stays below -54).",
                        "干岛雕刻时使用的水位（-54 以下仍是岩浆）。", "Default: -54")
                .define("carverWaterTable", -54, o -> o instanceof Integer v && v >= -64 && v <= 63);
        FORCE_ALL_DRY = builder.comment("Force every island dry, including the ones that would otherwise keep water (for players who want a completely dry world).",
                        "强制所有岛都是干洞，连本该保留水的水淹岛也不例外（想要一个完全干燥世界的玩家可以打开）。",
                        "Default: false")
                .define("forceAllDry", false);
        BOWL_SHELL_MIN = builder.comment("Least solid rock kept under a bowl island's basin floor (blocks).",
                        "Used when the basin is cut, so a deep basin cannot break through its own floor; the",
                        "floor is not refilled after carving - the rim is what keeps the water in.",
                        "碗形岛盆底下方至少保留的实心岩层（格）：用于挖盆时防止盆底穿透自身；雕刻之后不再回填",
                        "盆底，挡水靠碗沿。", "Default: 16")
                .define("bowlShellMin", 16, o -> o instanceof Integer v && v >= 0 && v <= 64);
        TOP_MARGIN = builder.comment("Headroom kept under the build limit when an island's own height would",
                        "reach it (blocks). / 岛体过高时，距建筑上限保留的余量（格）。", "Default: 8")
                .define("topMargin", 8, o -> o instanceof Integer v && v >= 0 && v <= 64);
        BOTTOM_MARGIN = builder.comment("Headroom kept above the world floor when an island's own depth would",
                        "reach it (blocks). Raise it to keep islands clear of the bottom of the world.",
                        "岛体过深时，距世界底部保留的余量（格）。调大可以让岛底离世界底更远。", "Default: 8")
                .define("bottomMargin", 8, o -> o instanceof Integer v && v >= 0 && v <= 64);
        THICKNESS_SCALE = builder.comment("Multiplier on how far an island reaches down from its centre. / 岛体自中心向下的深度乘数。",
                        "1.0 = the shipped shape (roughly 120-226 blocks from top to bottom at radius 80-320).",
                        "Raise it to fit larger underground structures inside an island; volume grows linearly with",
                        "it, so generation costs more and large islands reach the world floor and get cut flat",
                        "sooner. Lower it for lighter generation and thinner islands (more structures are then",
                        "skipped for not fitting). Only the downward half is scaled - the top stays put, so an",
                        "island keeps its clearance from the build limit either way.",
                        "1.0 = 默认形状（半径 80~320 时上下合计约 120~226 格）。调大可以让更大的地下结构装进岛里，",
                        "但岛体体积线性增长 ⇒ 生成更慢、大岛更早触到世界底而被削平；调小则更轻更薄（会有更多结构",
                        "因为装不下而被跳过）。只缩放向下的那一半，顶部不动，所以顶部余量不受影响。",
                        "属于世界生成设置：已生成的世界保持创建时的取值。", "Default: 1.0")
                .defineInRange("thicknessScale", 1.0, 0.3, 2.0);
        REPAIR_RIM_FROM = builder.comment("Air repair covers this much of the island's outer radius (0..1).",
                        "边缘补洞覆盖的外圈比例（0~1）。", "Default: 0.8")
                .defineInRange("repairRimFrom", 0.8, 0.0, 1.0);
        builder.pop();

        builder.comment("Structures / 结构").push("structures");
        STRUCTURE_UNDERSIDE_MARGIN = builder.comment("Clearance between a structure's lowest block and the island's",
                        "underside (blocks). / 结构最低点与岛底之间要求的最小间隙（格）。", "Default: 4")
                .define("undersideMargin", 4, o -> o instanceof Integer v && v >= 0 && v <= 64);
        STRUCTURE_RIM_MARGIN = builder.comment("Extra margin a structure's bounding box must keep from the island rim",
                        "(blocks). 0 = a structure may touch the rim.", "Raise it to stop structures from hugging the edge;",
                        "the cost is that more of them are skipped. / 结构包围盒距岛沿要求额外留出的余量（格）。",
                        "0 = 允许贴沿；调大可以避免结构贴边，代价是被跳过的结构变多。", "Default: 0")
                .define("rimMargin", 0, o -> o instanceof Integer v && v >= 0 && v <= 64);
        STRUCTURE_HEADROOM = builder.comment("Extra space reserved above the default band for structures (blocks).",
                        "0 by default: the default height layers already stop at 128, so everything above them is",
                        "free for structures and an island only keeps topMargin clear of the build limit.",
                        "Raise it only if a pack's structures need more room than that - it lowers every island.",
                        "在**默认高度带之上**额外为结构预留的空间（格）。默认 0：默认层最高 128，上方本来就全是",
                        "给结构的，岛只要和建筑上限保持 topMargin 的距离即可。只有整合包的结构确实更高时才调大",
                        "（它会把所有岛压低）。", "Default: 0")
                .define("headroom", 0, o -> o instanceof Integer v && v >= 0 && v <= 192);
        builder.pop();

        builder.comment("Biome pool / 群系池",
                        "Blunt switch first, precise lists as the fallback / 先一刀切，再用精确名单兜底").push("biomes");
        ALLOW_MOD_BIOMES = builder.comment("Accept biomes from other mods at all (blunt switch).",
                        "是否接受其它模组的群系（一刀切开关）。", "Default: true")
                .define("allowModBiomes", true);
        NAMESPACE_BLACKLIST = builder.comment("Whole namespaces to skip, e.g. [\"compactmachines\"].",
                        "整体跳过的命名空间，例如 [\"compactmachines\"]。", "Default: [\"compactmachines\"]")
                .defineListAllowEmpty("namespaceBlacklist", List.of("compactmachines"), () -> "modid",
                        o -> o instanceof String);
        BIOME_BLACKLIST = builder.comment("Single biomes to skip, e.g. [\"allthemodium:the_beyond\"].",
                        "单独跳过的群系，例如 [\"allthemodium:the_beyond\"]。",
                        "Default: useless_mod:useless_biome, allthemodium:the_beyond, ae2:spatial_storage")
                .defineListAllowEmpty("biomeBlacklist",
                        List.of("useless_mod:useless_biome", "allthemodium:the_beyond", "ae2:spatial_storage"),
                        () -> "modid:biome", o -> o instanceof String);
        BIOME_ALLOWLIST = builder.comment("Biomes to force back in, whatever any list or heuristic says",
                        "(checked before the blacklists; use it to rescue a biome that was dropped).",
                        "强制放回的群系（在名单之前判定；用来把被误杀的群系救回来）。", "Default: []")
                .defineListAllowEmpty("biomeAllowlist", List.of(), () -> "modid:biome", o -> o instanceof String);
        FUNCTIONAL_KEYWORDS = builder.comment("Id keywords of functional biomes (matched on the whole path or after",
                        "an underscore, and only when the biome lists no feature). / 功能性群系的 id 关键字（整段匹配或",
                        "下划线后缀匹配，且仅在该群系没有任何地物时才生效）。", "Default: [\"orbit\"]")
                .defineListAllowEmpty("functionalKeywords", List.of("orbit"), () -> "keyword",
                        o -> o instanceof String);
        EXCLUDE_FEATURELESS_BIOMES = builder.comment("Skip every biome without a placed feature (very blunt, off by",
                        "default - mod dimension biomes can be featureless yet real). / 跳过所有没有地物的群系（很粗暴，",
                        "默认关闭——模组维度群系可能没有地物但仍然是真实地貌）。", "Default: false")
                .define("excludeFeaturelessBiomes", false);
        EXCLUDE_FUNCTIONAL_BIOMES = builder.comment("Skip biomes that look purely functional (no features, no dimension",
                        "tag). / 跳过看起来纯功能性的群系（无地物、无维度标签）。", "Default: false")
                .define("excludeFunctionalBiomes", false);
        LOG_POOL = builder.comment("Log the biome pool decisions at server start (diagnostics).",
                        "启动时输出群系池的判定日志（诊断用）。",
                        "This is for us, not for players: turn it on when you are asked to, or when you are",
                        "chasing a biome that did not show up. / 这是我们自己排查用的，玩家不需要：被要求提供日志时、",
                        "或者正在追某个没出现的群系时再打开。", "Default: false")
                .define("logPool", false);
        LOG_LAYOUT = builder.comment("Log the island layout dump at server start (diagnostics: gaps, radius spread,",
                        "per-biome nearest island). / 启动时输出岛屿布局 dump（诊断用：间距/半径分布/每群系最近岛）。",
                        "Also for us only - it is long, and it answers questions a player never asks.",
                        "同样只给我们自己看：内容很长，回答的都是玩家不会问的问题。", "Default: false")
                .define("logLayout", false);
        builder.pop();

        SPEC = builder.build();
        // NOTE: snapshot() must NOT run here - reading a ModConfigSpec value before the config is loaded
        // throws "Cannot get config value before config is loaded" and takes mod loading down with it
        // (2026-10-11). The load event below is the earliest safe point.
    }

    private BrokenConfig() {
    }

    /** Copies every configured value into the fields worldgen reads. Called on load and on reload. */
    static void snapshot() {
        int minRadius = MIN_RADIUS.get();
        int maxRadius = MAX_RADIUS.get();
        if (minRadius > maxRadius) {
            FlightRingMod.LOGGER.warn("[Config] minRadius {} > maxRadius {} - swapping them", minRadius, maxRadius);
            int swap = minRadius;
            minRadius = maxRadius;
            maxRadius = swap;
        }
        BrokenIslandLayout.MIN_RADIUS = minRadius;
        BrokenIslandLayout.MAX_RADIUS = maxRadius;
        int gapMin = GAP_MIN.get();
        int gapMax = GAP_MAX.get();
        if (gapMin > gapMax) {
            FlightRingMod.LOGGER.warn("[Config] gapMin {} > gapMax {} - swapping them", gapMin, gapMax);
            int swap = gapMin;
            gapMin = gapMax;
            gapMax = swap;
        }
        BrokenIslandLayout.GAP_MIN = gapMin;
        BrokenIslandLayout.GAP_MAX = gapMax;
        BrokenIslandLayout.MAX_STRETCH = MAX_STRETCH.get();
        BrokenIslandLayout.MAX_JITTER = (gapMax - gapMin) / 4;
        List<Integer> layers = new ArrayList<>();
        for (Integer layer : HEIGHT_LAYERS.get()) {
            if (layer == null || layer < -64 || layer > 256) {
                FlightRingMod.LOGGER.warn("[Config] ignoring height layer {} (allowed -64..256)", layer);
            } else {
                layers.add(layer);
            }
        }
        if (layers.isEmpty()) {
            layers = new ArrayList<>(List.of(96, 112, 128));
            FlightRingMod.LOGGER.warn("[Config] no usable heightLayers - falling back to {}", layers);
        }
        BrokenIslandLayout.HEIGHT_LAYERS = layers.stream().mapToInt(Integer::intValue).toArray();

        BrokenIslandChunkGenerator.WET_ISLAND_CHANCE = WET_ISLAND_CHANCE.get();
        BrokenIslandChunkGenerator.CARVER_WATER_TABLE = CARVER_WATER_TABLE.get();
        BrokenIslandChunkGenerator.FORCE_ALL_DRY = FORCE_ALL_DRY.get();
        BrokenIslandChunkGenerator.BOWL_SHELL_MIN = BOWL_SHELL_MIN.get();
        BrokenIslandChunkGenerator.REPAIR_RIM_FROM = REPAIR_RIM_FROM.get();
        BrokenIslandChunkGenerator.TOP_MARGIN = TOP_MARGIN.get();
        BrokenIslandChunkGenerator.BOTTOM_MARGIN = BOTTOM_MARGIN.get();
        BrokenIslandChunkGenerator.THICKNESS_SCALE = THICKNESS_SCALE.get();
        BrokenIslandChunkGenerator.STRUCTURE_UNDERSIDE_MARGIN = STRUCTURE_UNDERSIDE_MARGIN.get();
        BrokenIslandChunkGenerator.STRUCTURE_HEADROOM = STRUCTURE_HEADROOM.get();
        BrokenIslandChunkGenerator.STRUCTURE_RIM_MARGIN = STRUCTURE_RIM_MARGIN.get();

        BrokenBiomePool.allowModBiomes = ALLOW_MOD_BIOMES.get();
        BrokenBiomePool.excludeFeaturelessBiomes = EXCLUDE_FEATURELESS_BIOMES.get();
        BrokenBiomePool.excludeFunctionalBiomes = EXCLUDE_FUNCTIONAL_BIOMES.get();
        BrokenBiomePool.logPool = LOG_POOL.get();
        BrokenIslandLayout.logLayout = LOG_LAYOUT.get();
        BrokenBiomePool.NAMESPACE_BLACKLIST = withoutPlaceholders(NAMESPACE_BLACKLIST.get());
        BrokenBiomePool.FUNCTIONAL_KEYWORDS = withoutPlaceholders(FUNCTIONAL_KEYWORDS.get());
        BrokenBiomePool.BIOME_BLACKLIST = parseIds(BIOME_BLACKLIST.get());
        BrokenBiomePool.ALLOWED_BIOMES = parseIds(BIOME_ALLOWLIST.get());
    }

    /**
     * Placeholder values the config screen inserts when the user presses "+" on a list and does not edit the new
     * entry. They must never reach worldgen: "modid"/"modid:biome" would sit in a list doing nothing, and
     * "keyword" could silently match a real biome id.
     */
    private static final List<String> PLACEHOLDERS = List.of("modid", "modid:biome", "keyword");

    /** Drops blank entries and the screen's placeholders, warning about every entry it drops. */
    private static List<String> withoutPlaceholders(List<? extends String> raw) {
        List<String> kept = new ArrayList<>(raw.size());
        for (String entry : raw) {
            String trimmed = entry == null ? "" : entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (PLACEHOLDERS.contains(trimmed)) {
                FlightRingMod.LOGGER.warn(
                        "[Config] ignoring placeholder list entry \"{}\" - edit it into a real value", trimmed);
                continue;
            }
            kept.add(trimmed);
        }
        return kept;
    }

    private static List<ResourceLocation> parseIds(List<? extends String> raw) {
        List<ResourceLocation> ids = new ArrayList<>(raw.size());
        for (String entry : withoutPlaceholders(raw)) {
            ResourceLocation id = ResourceLocation.tryParse(entry.trim());
            if (id == null) {
                FlightRingMod.LOGGER.warn("[Config] ignoring malformed biome id \"{}\"", entry);
            } else {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * Snapshots the config once mod loading has finished, and reports what it read.
     * <p>
     * Registered from the mod constructor against {@code FMLCommonSetupEvent} (the same style as the rest of
     * this mod, and free of the deprecated {@code @EventBusSubscriber(bus = ...)} form).
     * <p>
     * Deliberately <b>not</b> done from {@code ModConfigEvent}: that event fires for every mod's config, and
     * reading our own values while another mod's config is loading throws "Cannot get config value before
     * config is loaded" and takes all of mod loading down with it. That is exactly what crashed the user's
     * modpack (2026-10-13 report: {@code BrokenConfig.snapshot} &lt;- {@code onLoad}), while dev - no other mods -
     * never showed it. {@code FMLCommonSetupEvent} runs after every config is loaded, and
     * {@code BrokenBiomePool#onServerAboutToStart} snapshots again so editing the file plus a server restart
     * is enough.
     */
    static void snapshotAndLog() {
        snapshot();
        // Log the values worldgen actually uses (after the swaps and filters above), not the raw config, so a
        // corrected min/max pair does not show up here looking backwards.
        FlightRingMod.LOGGER.info(
                "[Config] broken dimension config loaded: radius {}..{}, gap {}..{}, wet island 1/{}",
                BrokenIslandLayout.MIN_RADIUS, BrokenIslandLayout.MAX_RADIUS,
                BrokenIslandLayout.GAP_MIN, BrokenIslandLayout.GAP_MAX,
                BrokenIslandChunkGenerator.WET_ISLAND_CHANCE);
    }
}
