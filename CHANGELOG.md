# Changelog

## 0.1.0 (2026-10-31)

First release.


- Keep fully evolved Pokémon out of wild spawns, worked out from Cobblemon's evolution data, per regional form
- Also block fully evolved Pokémon offered by Cobblemon 1.8 habitats, which add spawns outside the spawn pool
- Trim herds led by evolved forms instead of removing them, with a configurable max herd size
- Anti-cluster rule for natural spawns, including herd spawns, with regional forms counted separately
- Turn the anti-cluster rule off per dimension
- Optional: raise evolved Pokémon that would spawn below their evolution level
- `/smartspawns`, `/smartspawns reload`, `/smartspawns check <species> [form]` and `/smartspawns pool <species>`
- `-Dsmartspawns.debug=true` logs every natural spawn and the entry it came from
- Config is created once and never rewritten
- Supports Cobblemon 1.7.3 and 1.8.x on Minecraft 1.21.1
