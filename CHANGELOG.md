# Changelog

## 0.1.0 (unreleased)

- Keep fully evolved Pokémon out of wild spawns, worked out from Cobblemon's evolution data, per regional form
- Trim herds led by evolved forms instead of removing them, with a configurable max herd size
- Anti-cluster rule for natural spawns, including herd spawns, with regional forms counted separately
- Turn the anti-cluster rule off per dimension
- Optional: raise evolved Pokémon that would spawn below their evolution level
- `/smartspawns`, `/smartspawns reload` and `/smartspawns check <species> [form]`
- Config is created once and never rewritten
- Supports Cobblemon 1.7.3 and 1.8.x on Minecraft 1.21.1
