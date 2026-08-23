# Simple Flight Ring

**A small Minecraft mod that adds six tiers of flight rings: Wood, Stone, Iron, Gold, Diamond and Netherite.**

Fly like in Creative mode by simply wearing (or carrying) a ring — no elytra, no rockets, just **traditional Creative flight**: double-tap Space to take off and land, hold Space to ascend, hold Shift to descend.

Supported versions: **Minecraft 1.21.1 (NeoForge 21.1.x)** and **Minecraft 26.1.2 (NeoForge 26.1.2.x)**.

---

## Features

- ⚡ **Traditional Creative flight** — the ring grants `mayfly` while it has durability left. Double-tap Space to toggle flying, exactly like Creative mode.
- ⏱️ **Durability only drains while flying** — 1 durability point per second of flight. Standing still or on the ground costs nothing.
- 🔨 **Unbreaking extends flight time** — each Unbreaking level makes every durability point last one extra second (Unbreaking III = 1 point per 4 seconds).
- 💚 **Mending compatible** — rings repair naturally from XP orbs.
- 💍 **Six tiers, nested upgrade crafting** — craft the Wooden ring first, then upgrade it through Stone → Iron → Gold → Diamond in a 3×3 crafting grid (8 material items around the lower-tier ring).
  - Upgrades **keep enchantments, custom names and lore**.
  - The input ring may have **any remaining durability** (even zero).
  - The upgraded ring starts at **full durability**.
- ⚒️ **Netherite ring via smithing** — Netherite Upgrade Smithing Template + Diamond Flight Ring + Netherite Ingot in the smithing table.
- 💜 **Indestructible Core** — craft 1 Netherite Ingot + 1 Nether Star (shapeless) into the dark *Indestructible Core*, then combine it with **any** flight ring in the smithing table: the ring becomes **indestructible** — durability never drains, flight is infinite, the tooltip shows *Infinite* flight time and the HUD countdown disappears (no longer needed).
- 🔧 **Crafting-table repair** — combine a ring with its tier material (shapeless): each craft consumes 1 material unit and restores 25% durability, and a single Netherite Ingot fully repairs the Netherite ring. Enchantments, names and lore are preserved.
- 🖥️ **HUD flight time countdown** — shows the remaining flight time in the bottom-left corner by default.
  - Multiple rings are **summed up** (Curios slot + inventory + offhand + backpacks).
  - Automatically hidden while the chat is open (configurable).
  - Position and visibility configurable in `config/flightring-client.toml`.
- 🎛️ **Cloth Config support (optional)** — in-game configuration screen from the mod list (toggle + position sliders).
- 🎒 **Sophisticated Backpacks support (optional)** — rings stored inside sophisticated backpacks also grant flight, including nested backpacks (up to 3 levels) and backpacks worn in the armor/offhand/Curios slots.
- 🧿 **Curios API support (optional)** — adds an extra **"Flight Ring"** curio slot; rings can be right-click equipped. Without Curios, rings simply work from the inventory (and still do even with Curios installed).

## Flight time per tier

| Ring | Total flight time | Durability |
|------|-------------------|------------|
| Wooden | 5 minutes | 300 |
| Stone | 15 minutes | 900 |
| Iron | 30 minutes | 1800 |
| Golden | 60 minutes | 3600 |
| Diamond | 120 minutes | 7200 |
| Netherite | 240 minutes | 14400 |

> Durability = minutes × 60 (1 point per second of flight). With Unbreaking III the actual flight time is about 4× the table above, and Mending makes the ring effectively infinite.

## Crafting

### Wooden Flight Ring
```
Planks        Planks        Planks
Planks         Feather      Planks
Planks        Planks        Planks
```
> Any planks work (`#minecraft:planks` tag) — all vanilla wood types and planks from other mods are accepted.

### Upgrades (Stone / Iron / Gold / Diamond)
8 material items around the lower-tier ring (any durability):

| Upgrade to | 8× material | Center |
|-----------|-------------|--------|
| Stone Flight Ring | Cobblestone* | Wooden Flight Ring |
| Iron Flight Ring | Iron Ingot | Stone Flight Ring |
| Golden Flight Ring | Gold Ingot | Iron Flight Ring |
| Diamond Flight Ring | Diamond | Golden Flight Ring |

> \* Any cobblestone works (`#c:cobblestones` tag) — regular cobblestone, deepslate cobblestone and cobblestone from other mods are all accepted.

### Repairing (Shapeless)
Combine a flight ring with its tier material in any arrangement to restore durability:

| Ring | Material | Restored per craft |
|------|----------|--------------------|
| Wooden Flight Ring | any planks | 25% |
| Stone Flight Ring | any cobblestone | 25% |
| Iron Flight Ring | Iron Ingot | 25% |
| Golden Flight Ring | Gold Ingot | 25% |
| Diamond Flight Ring | Diamond | 25% |
| Netherite Flight Ring | Netherite Ingot | **100%** (1 ingot = full repair) |

- Each craft consumes exactly **1 material unit** (standard vanilla consumption) — repeat the craft to fully repair a ring. This works identically in every crafting system: crafting table, recipe book and modded terminals such as AE2's crafting terminal.
- Enchantments, custom names and lore are **kept** during repair.
- A fully-durable ring cannot be used in the repair recipe (no waste).

### Netherite Flight Ring (Smithing Table)
1. Netherite Upgrade Smithing Template
2. Diamond Flight Ring (any durability)
3. Netherite Ingot

## Installation

1. Install the matching [NeoForge](https://neoforged.net/) version.
2. Drop the `flightring` jar into the `mods` folder.
3. *(Optional)* Install [Curios API](https://modrinth.com/mod/curios) for the flight ring slot, [Cloth Config](https://modrinth.com/mod/cloth-config) for the in-game config screen, and/or [Sophisticated Backpacks](https://modrinth.com/mod/sophisticated-backpacks) for backpack support.

## Configuration

`config/flightring-client.toml` (client-side):

| Option | Default | Description |
|--------|---------|-------------|
| `showFlightTimer` | `true` | Show the HUD flight time countdown |
| `hideWhileChatOpen` | `true` | Hide the countdown while the chat is open |
| `hudX` | `4` | Horizontal position (pixels from the left edge) |
| `hudY` | `4` | Vertical position (pixels from the bottom edge) |

## Notes

- Creative/Spectator players are not affected (vanilla flight, no durability cost).
- A fully consumed ring does not break — it becomes inert, and can still be used in upgrade recipes or repaired with the repair recipe / Mending / anvil.
- The mod id is `flightring`; all features are server-safe and work in multiplayer.

## License

MIT
