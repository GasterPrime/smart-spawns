# Smart Spawns

Server-side spawn rules for [Cobblemon](https://modrinth.com/mod/cobblemon).

- **No fully evolved Pokémon in the wild.** Venusaur, Raichu and Gyarados stop spawning, so players catch the earlier stage and evolve it. Single-stage Pokémon such as Tauros, Lapras and Ditto still spawn.
- **No same-species clusters.** A Pokémon is less likely to spawn next to wild Pokémon of the same species, and won't spawn at all once a few are already there. Herds are included.
- **Herds that make sense.** Herds led by an evolved form are trimmed instead of removed, because some species only ever spawn in herds.

Final stages are worked out from Cobblemon's own evolution data when the server starts, so new species and species added by other addons are covered without a list to maintain. Regional forms are checked on their own: Alolan Raichu is a final stage separately from Raichu.

Players do not need to install anything. Install it on the server only.

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | 0.17.2 or newer |
| Fabric API | any 1.21.1 build |
| Cobblemon | 1.7.3 or 1.8.x |

Fabric Language Kotlin is already bundled inside Cobblemon.

## Commands

All commands need operator level 2 or the server console.

| Command | What it does |
|---|---|
| `/smartspawns` | Shows how many spawn entries and species are kept out, and the anti-cluster settings |
| `/smartspawns reload` | Reads the config again and re-applies it, no restart needed |
| `/smartspawns check <species> [form]` | Shows what a species evolves from and into, and whether it can spawn in the wild. Example: `/smartspawns check raichu alolan` |

## Config

`config/smartspawns/config.json` is created on first start. Smart Spawns never rewrites it after that, so your edits stay as you left them. Run `/smartspawns reload` after editing.

```json
{
  "blockFinalEvolutions": true,
  "allowSpecies": [],
  "blockSpecies": [],
  "herds": {
    "maxHerdSize": 1
  },
  "antiCluster": {
    "enabled": true,
    "radius": 24,
    "verticalRadius": 12,
    "maxNearby": 2,
    "weightPerNearby": 0.3,
    "rememberSeconds": 20,
    "separateForms": true,
    "disabledWorlds": []
  },
  "underleveledEvolutions": {
    "raiseToEvolutionLevel": false
  }
}
```

| Setting | Default | Meaning |
|---|---|---|
| `blockFinalEvolutions` | `true` | Keep fully evolved Pokémon out of wild spawns |
| `allowSpecies` | `[]` | Final stages that may still spawn, e.g. `["gyarados"]` |
| `blockSpecies` | `[]` | Extra species to keep out, even when `blockFinalEvolutions` is off |
| `herds.maxHerdSize` | `1` | Largest herd that can spawn at once. `0` keeps each herd's own size |
| `antiCluster.enabled` | `true` | Turn the anti-cluster rule on or off |
| `antiCluster.radius` | `24` | Horizontal distance to look for the same species (4–64) |
| `antiCluster.verticalRadius` | `12` | Vertical distance to look for the same species (2–64) |
| `antiCluster.maxNearby` | `2` | At this many nearby, the species stops spawning there (1–20) |
| `antiCluster.weightPerNearby` | `0.3` | Spawn chance multiplier for each one nearby (0–1). With the defaults, one nearby leaves 30% |
| `antiCluster.rememberSeconds` | `20` | How long to count spawns that were ordered but have not appeared yet (0–120) |
| `antiCluster.separateForms` | `true` | Count regional forms as their own species |
| `antiCluster.disabledWorlds` | `[]` | Dimension ids where the anti-cluster rule is off, e.g. `["minecraft:the_nether"]` |
| `underleveledEvolutions.raiseToEvolutionLevel` | `false` | When an evolved Pokémon would spawn below the level it evolves at, raise it to that level. Useful if you allow final stages |

`config/smartspawns/blocked-species.txt` lists what is currently kept out. It is rewritten on every start and reload, so edit `config.json` instead.

## Good to know

- Species are removed from Cobblemon's world spawn pool itself, so fishing and Poké Snacks follow the same rule.
- The anti-cluster rule applies to natural spawns around players.
- Lowering the overall spawn rate is not part of this mod. Use `pokemonPerChunk` in `config/cobblemon/main.json`.

## Building

Gradle needs Java 25 to run (the mod itself targets Java 21).

```bash
./gradlew build
```

The jar ends up in `build/libs/`.

## Bugs and ideas

Open an issue at [github.com/GasterPrime/smart-spawns/issues](https://github.com/GasterPrime/smart-spawns/issues).

## License

Smart Spawns is licensed under the [Mozilla Public License 2.0](LICENSE).
Copyright (c) 2026 GasterPrime.
