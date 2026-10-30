/*
 * Copyright (c) 2026 GasterPrime
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gasterprime.smartspawns

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * config/smartspawns/config.json
 *
 * The file is written once, when it is missing. After that the mod only reads it, so server owners
 * can reorder or extend it without the mod resetting their edits.
 */
class SmartSpawnsConfig {
    var blockFinalEvolutions = true
    var allowSpecies: MutableList<String> = mutableListOf()
    var blockSpecies: MutableList<String> = mutableListOf()
    var herds = Herds()
    var antiCluster = AntiCluster()
    var underleveledEvolutions = Underleveled()

    class Herds {
        /** 0 keeps each herd's own size. 1 makes every herd spawn one Pokémon at a time. */
        var maxHerdSize = 1
    }

    class AntiCluster {
        var enabled = true
        var radius = 24
        var verticalRadius = 12
        var maxNearby = 2
        var weightPerNearby = 0.3
        var rememberSeconds = 20
        /** Count regional forms as their own species, so an Alolan Vulpix does not hold back a normal one. */
        var separateForms = true
        /** Dimension ids where the anti-cluster rule is off, such as "minecraft:the_nether". */
        var disabledWorlds: MutableList<String> = mutableListOf()
    }

    class Underleveled {
        /** Raise the level of evolved Pokémon that would spawn below the level they evolve at. */
        var raiseToEvolutionLevel = false
    }

    // Gson leaves a field null when the file sets it to null, even though Kotlin says it cannot be.
    @Suppress("SENSELESS_COMPARISON")
    private fun normalize(): SmartSpawnsConfig {
        if (allowSpecies == null) allowSpecies = mutableListOf()
        if (blockSpecies == null) blockSpecies = mutableListOf()
        if (herds == null) herds = Herds()
        if (antiCluster == null) antiCluster = AntiCluster()
        if (antiCluster.disabledWorlds == null) antiCluster.disabledWorlds = mutableListOf()
        if (underleveledEvolutions == null) underleveledEvolutions = Underleveled()

        herds.maxHerdSize = herds.maxHerdSize.coerceIn(0, 20)
        antiCluster.radius = antiCluster.radius.coerceIn(4, 64)
        antiCluster.verticalRadius = antiCluster.verticalRadius.coerceIn(2, 64)
        antiCluster.maxNearby = antiCluster.maxNearby.coerceIn(1, 20)
        antiCluster.weightPerNearby = antiCluster.weightPerNearby.coerceIn(0.0, 1.0)
        antiCluster.rememberSeconds = antiCluster.rememberSeconds.coerceIn(0, 120)
        return this
    }

    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        val folder: Path
            get() = FabricLoader.getInstance().configDir.resolve(SmartSpawns.MOD_ID)

        private val file: Path
            get() = folder.resolve("config.json")

        fun load(): SmartSpawnsConfig {
            if (Files.notExists(file)) {
                val defaults = SmartSpawnsConfig()
                writeText(file, GSON.toJson(defaults))
                return defaults
            }
            return try {
                Files.newBufferedReader(file).use { reader ->
                    (GSON.fromJson(reader, SmartSpawnsConfig::class.java) ?: SmartSpawnsConfig()).normalize()
                }
            } catch (e: Exception) {
                SmartSpawns.LOGGER.error("[Smart Spawns] Could not read {}, using defaults until it is fixed", file, e)
                SmartSpawnsConfig()
            }
        }

        fun writeText(path: Path, text: String) {
            try {
                Files.createDirectories(path.parent)
                Files.writeString(path, text)
            } catch (e: Exception) {
                SmartSpawns.LOGGER.error("[Smart Spawns] Could not write {}", path, e)
            }
        }
    }
}
