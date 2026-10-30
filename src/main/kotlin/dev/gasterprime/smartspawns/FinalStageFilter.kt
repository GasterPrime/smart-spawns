/*
 * Copyright (c) 2026 GasterPrime
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gasterprime.smartspawns

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool
import java.util.IdentityHashMap
import java.util.TreeSet

/**
 * Takes fully evolved Pokémon out of Cobblemon's world spawn pool.
 *
 * Entries are removed from the pool itself rather than cancelled at spawn time. Natural spawns, fishing,
 * Poké Snacks and any mod that reads the pool then agree, and the remaining species keep their usual odds.
 *
 * Herds need extra care: many herds are led by an evolved form (Rapidash with Ponyta, alpha Venusaur with
 * Bulbasaur), and 19 species only ever spawn in herds, so herds are trimmed instead of dropped.
 */
object FinalStageFilter {

    private class HerdOriginal(
        val members: List<PokemonHerdSpawnDetail.Herdable>,
        val maxHerdSize: Int,
        val roles: List<HerdRoles.Snapshot>,
    )

    private val removed = mutableListOf<SpawnDetail>()
    private val herds = IdentityHashMap<PokemonHerdSpawnDetail, HerdOriginal>()
    private var removedFrom: SpawnPool? = null
    private var subscribed = false

    var blockedNames: Set<String> = emptySet()
        private set
    var removedCount = 0
        private set
    var totalCount = 0
        private set
    var herdsChanged = 0
        private set

    /** Re-filters whenever Cobblemon reloads its spawn pool, e.g. after /reload. */
    fun subscribe() {
        if (subscribed) return
        try {
            CobblemonSpawnPools.WORLD_SPAWN_POOL.observable.subscribe(Priority.LOWEST) { _ ->
                // A reloaded pool holds fresh entries, so what was removed from the old one no longer applies.
                synchronized(this) {
                    removed.clear()
                    herds.clear()
                    removedFrom = null
                }
                val server = SmartSpawns.server
                if (server != null) server.execute(::apply) else apply()
            }
            subscribed = true
        } catch (e: Throwable) {
            SmartSpawns.LOGGER.error("[Smart Spawns] Could not watch Cobblemon spawn pool reloads. Fully evolved spawns return after /reload until a restart.", e)
        }
    }

    /** Puts back everything removed earlier, then filters the pool again with the current config. */
    @Synchronized
    fun apply() {
        val pool = runCatching { CobblemonSpawnPools.WORLD_SPAWN_POOL }.getOrNull() ?: return
        val config = SmartSpawns.config
        val details = pool.details

        if (removedFrom === pool) {
            // Entries removed before Cobblemon's server-load step never got initialised, so do it on the way back.
            SmartSpawns.server?.let { server -> removed.forEach { runCatching { it.onServerLoad(server) } } }
            details.addAll(removed)
            restoreHerds()
        }
        removed.clear()
        herds.clear()
        removedFrom = pool

        val allow = SpeciesData.names(config.allowSpecies)
        val block = SpeciesData.names(config.blockSpecies)
        val names = TreeSet<String>()
        val total = details.size
        var changed = 0
        val keep = ArrayList<SpawnDetail>(details.size)

        for (detail in details) {
            if (detail is PokemonHerdSpawnDetail) {
                when (val result = trimHerd(detail, config, allow, block, names)) {
                    HerdResult.REMOVE -> removed += detail
                    else -> {
                        if (result == HerdResult.CHANGED) changed++
                        keep += detail
                    }
                }
                continue
            }
            val blockedAs = (detail as? PokemonSpawnDetail)?.let { blockedName(it.pokemon, config, allow, block) }
            if (blockedAs == null) {
                keep += detail
            } else {
                removed += detail
                names += blockedAs
            }
        }

        if (removed.isNotEmpty()) {
            details.clear()
            details.addAll(keep)
        }
        try {
            pool.precalculate()
        } catch (e: Throwable) {
            SmartSpawns.LOGGER.error("[Smart Spawns] Could not recalculate the spawn pool", e)
        }

        blockedNames = names
        removedCount = removed.size
        totalCount = total
        herdsChanged = changed
        writeBlockedList(names)
        SmartSpawns.LOGGER.info(
            "[Smart Spawns] Removed {} of {} spawn entries, trimmed {} herds, {} species kept out of the wild (see config/smartspawns/blocked-species.txt)",
            removedCount, totalCount, herdsChanged, names.size,
        )
    }

    private enum class HerdResult { UNCHANGED, CHANGED, REMOVE }

    private fun trimHerd(
        herd: PokemonHerdSpawnDetail,
        config: SmartSpawnsConfig,
        allow: Set<String>,
        block: Set<String>,
        names: MutableSet<String>,
    ): HerdResult {
        val members = herd.herdablePokemon
        if (members.isNullOrEmpty()) return HerdResult.UNCHANGED

        val kept = ArrayList<PokemonHerdSpawnDetail.Herdable>()
        var droppedAlphaLeader = false
        var keptLeader = false
        for (member in members) {
            val blockedAs = blockedName(member.pokemon, config, allow, block)
            if (blockedAs == null) {
                kept += member
                keptLeader = keptLeader || HerdRoles.isLeader(member)
            } else {
                names += blockedAs
                if (HerdRoles.isLeader(member) && isAlpha(member.pokemon)) droppedAlphaLeader = true
            }
        }

        val size = config.herds.maxHerdSize
        val resize = size > 0 && herd.maxHerdSize > size
        if (kept.size == members.size && !resize) return HerdResult.UNCHANGED
        // An alpha herd exists for its alpha. Without it, or without any members left, drop the whole entry.
        if (kept.isEmpty() || (droppedAlphaLeader && !keptLeader)) return HerdResult.REMOVE

        herds[herd] = HerdOriginal(ArrayList(members), herd.maxHerdSize, members.map(HerdRoles::snapshot))
        herd.herdablePokemon = ArrayList(kept)
        if (resize) herd.maxHerdSize = size
        // A herd that needs a leader would otherwise spawn nothing but its leader once it is cut down.
        if (size == 1 || !keptLeader) kept.forEach(HerdRoles::makeFollower)
        return HerdResult.CHANGED
    }

    private fun restoreHerds() {
        for ((herd, original) in herds) {
            original.members.forEachIndexed { i, member -> HerdRoles.restore(member, original.roles[i]) }
            herd.herdablePokemon = ArrayList(original.members)
            herd.maxHerdSize = original.maxHerdSize
        }
        herds.clear()
    }

    /** Null when the entry may spawn, otherwise the display name of what is blocked. */
    private fun blockedName(props: PokemonProperties?, config: SmartSpawnsConfig, allow: Set<String>, block: Set<String>): String? {
        val species = SpeciesData.species(props?.species) ?: return null
        val path = species.resourceIdentifier.path
        val form = SpeciesData.form(species, props?.aspects)
        if (path in block) return SpeciesData.displayName(species, form)
        if (!config.blockFinalEvolutions || path in allow) return null
        return if (SpeciesData.isFinalStage(form)) SpeciesData.displayName(species, form) else null
    }

    private fun isAlpha(props: PokemonProperties?): Boolean =
        runCatching { props?.asString(" ")?.contains("alpha", ignoreCase = true) == true }.getOrDefault(false)

    private fun writeBlockedList(names: Set<String>) {
        val text = buildString {
            append("# Species currently kept out of the wild. Smart Spawns rewrites this file on every start and reload.\n")
            append("# Edit config.json instead.\n")
            append("# ${names.size} species, $removedCount of $totalCount spawn entries removed, $herdsChanged herds trimmed\n")
            names.forEach { append(it).append('\n') }
        }
        SmartSpawnsConfig.writeText(SmartSpawnsConfig.folder.resolve("blocked-species.txt"), text)
    }
}
