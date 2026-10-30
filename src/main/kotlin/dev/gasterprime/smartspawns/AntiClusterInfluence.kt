/*
 * Copyright (c) 2026 GasterPrime
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gasterprime.smartspawns

import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnAction
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.AABB
import java.util.ArrayDeque
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.pow

/**
 * Keeps the same species from piling up in one spot. Added to every player's natural spawner.
 *
 * When Cobblemon weighs a candidate at a position, wild Pokémon of the same species around that position are
 * counted, together with spawns ordered a moment ago that have not appeared yet. At [SmartSpawnsConfig.AntiCluster.maxNearby]
 * the candidate's weight drops to 0 and Cobblemon picks something else; below that it shrinks by weightPerNearby per match.
 * Herd entries are checked against every species in the herd.
 *
 * Everything here runs on the server thread, inside the player spawner tick.
 */
object AntiClusterInfluence : SpawningInfluence {

    private class Planned(val level: ServerLevel, val x: Int, val y: Int, val z: Int, val key: String, val at: Long)

    private const val MAX_PLANNED = 5000

    private val planned = ArrayDeque<Planned>()
    private val keysOf = WeakHashMap<SpawnDetail, Set<String>>()
    private var lastPosition: SpawnablePosition? = null
    private var lastCounts: Map<String, Int> = emptyMap()
    private var lastAt = 0L
    private var lastError = 0L
    private var keysSeparateForms = true

    val plannedCount: Int
        get() = planned.size

    override fun affectWeight(detail: SpawnDetail, spawnablePosition: SpawnablePosition, weight: Float): Float {
        val rules = SmartSpawns.config.antiCluster
        if (!rules.enabled || weight <= 0f || isDisabledIn(spawnablePosition.world, rules)) return weight
        return try {
            val keys = keysFor(detail, rules.separateForms)
            if (keys.isEmpty()) return weight
            val counts = counts(spawnablePosition, rules)
            val nearby = keys.maxOf { counts[it] ?: 0 }
            when {
                nearby <= 0 -> weight
                nearby >= rules.maxNearby -> 0f
                else -> (weight * rules.weightPerNearby.pow(nearby)).toFloat()
            }
        } catch (e: Throwable) {
            logError(e)
            weight
        }
    }

    override fun affectAction(action: SpawnAction<*>) {
        if (action !is PokemonSpawnAction) return
        val config = SmartSpawns.config
        try {
            if (config.underleveledEvolutions.raiseToEvolutionLevel) raiseUnderleveled(action)
            remember(action, config.antiCluster)
        } catch (e: Throwable) {
            logError(e)
        }
    }

    /** Clears remembered spawns, e.g. after the config changes how species are keyed. */
    fun reset() {
        planned.clear()
        keysOf.clear()
        lastPosition = null
    }

    private fun remember(action: PokemonSpawnAction, rules: SmartSpawnsConfig.AntiCluster) {
        if (!rules.enabled || rules.rememberSeconds <= 0) return
        val position = action.spawnablePosition
        val key = SpeciesData.key(action.props, rules.separateForms) ?: return
        val pos = position.position
        val now = System.currentTimeMillis()
        prune(now, rules)
        planned.addLast(Planned(position.world, pos.x, pos.y, pos.z, key, now))
        // The next position in the same tick has to see this spawn too.
        lastPosition = null
    }

    private fun raiseUnderleveled(action: PokemonSpawnAction) {
        val species = SpeciesData.species(action.props.species) ?: return
        val minLevel = SpeciesData.evolutionLevel(SpeciesData.form(species, action.props.aspects)) ?: return
        val range = action.levelRange ?: return
        if (range.first < minLevel) {
            action.levelRange = minLevel..maxOf(range.last, minLevel)
        }
    }

    private fun keysFor(detail: SpawnDetail, separateForms: Boolean): Set<String> {
        if (separateForms != keysSeparateForms) {
            keysOf.clear()
            keysSeparateForms = separateForms
        }
        return keysOf.getOrPut(detail) {
            when (detail) {
                is PokemonSpawnDetail -> setOfNotNull(SpeciesData.key(detail.pokemon, separateForms))
                is PokemonHerdSpawnDetail -> detail.herdablePokemon.orEmpty()
                    .mapNotNull { SpeciesData.key(it.pokemon, separateForms) }
                    .toSet()
                else -> emptySet()
            }
        }
    }

    /** Species key to how many wild ones are around [position], including spawns just ordered. Cached per position. */
    private fun counts(position: SpawnablePosition, rules: SmartSpawnsConfig.AntiCluster): Map<String, Int> {
        val now = System.currentTimeMillis()
        // Cobblemon weighs every candidate species at the same position, so count once per position.
        if (position === lastPosition && now - lastAt < 1000L) return lastCounts

        val level = position.world
        val pos = position.position
        val out = HashMap<String, Int>()
        val box = AABB(pos).inflate(rules.radius.toDouble(), rules.verticalRadius.toDouble(), rules.radius.toDouble())
        for (entity in level.getEntitiesOfClass(PokemonEntity::class.java, box) { it.isAlive && !it.pokemon.isPlayerOwned() }) {
            val pokemon = entity.pokemon
            out.merge(SpeciesData.key(pokemon.species, pokemon.form, rules.separateForms), 1, Int::plus)
        }
        prune(now, rules)
        for (p in planned) {
            if (p.level === level && abs(p.x - pos.x) <= rules.radius && abs(p.z - pos.z) <= rules.radius &&
                abs(p.y - pos.y) <= rules.verticalRadius
            ) {
                out.merge(p.key, 1, Int::plus)
            }
        }
        lastPosition = position
        lastCounts = out
        lastAt = now
        return out
    }

    private fun prune(now: Long, rules: SmartSpawnsConfig.AntiCluster) {
        val keepFor = rules.rememberSeconds * 1000L
        while (planned.isNotEmpty() && now - planned.first().at > keepFor) planned.removeFirst()
        while (planned.size > MAX_PLANNED) planned.removeFirst()
    }

    private fun isDisabledIn(level: ServerLevel, rules: SmartSpawnsConfig.AntiCluster): Boolean =
        rules.disabledWorlds.isNotEmpty() && level.dimension().location().toString() in rules.disabledWorlds

    private fun logError(e: Throwable) {
        val now = System.currentTimeMillis()
        if (now - lastError > 60_000L) {
            lastError = now
            SmartSpawns.LOGGER.error("[Smart Spawns] Anti-cluster check failed, letting the spawn through", e)
        }
    }
}
