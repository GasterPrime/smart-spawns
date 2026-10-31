/*
 * Copyright (c) 2026 GasterPrime
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gasterprime.smartspawns

import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object SmartSpawns : ModInitializer {
    const val MOD_ID = "smartspawns"
    val LOGGER: Logger = LoggerFactory.getLogger("Smart Spawns")

    var config = SmartSpawnsConfig()
        private set
    var server: MinecraftServer? = null
        private set
    val version: String by lazy {
        FabricLoader.getInstance().getModContainer(MOD_ID).map { it.metadata.version.friendlyString }.orElse("?")
    }

    private var influenceInstalled = false

    override fun onInitialize() {
        config = SmartSpawnsConfig.load()
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> SmartSpawnsCommands.register(dispatcher) }

        // Player spawners are created when a player joins, so the influence has to be in place before that.
        ServerLifecycleEvents.SERVER_STARTING.register { startingServer ->
            server = startingServer
            installInfluence()
        }
        ServerLifecycleEvents.SERVER_STARTED.register { startedServer ->
            server = startedServer
            FinalStageFilter.subscribe()
            FinalStageFilter.apply()
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { server = null }
    }

    /** Reads the config again and re-filters the spawn pool, without a restart. */
    fun reload() {
        config = SmartSpawnsConfig.load()
        AntiClusterInfluence.reset()
        FinalStageFilter.apply()
    }

    private fun installInfluence() {
        if (influenceInstalled) return
        influenceInstalled = true
        try {
            PlayerSpawnerFactory.influenceBuilders.add { FinalStageInfluence }
            PlayerSpawnerFactory.influenceBuilders.add { AntiClusterInfluence }
        } catch (e: Throwable) {
            LOGGER.error("[Smart Spawns] Could not add spawn rules to player spawners", e)
        }
        // Fishing and Poké Snack spawners exist from Cobblemon 1.8 on. They only get the final-stage rule.
        try {
            installFishingAndSnacks()
        } catch (e: LinkageError) {
            LOGGER.info("[Smart Spawns] No fishing or Poké Snack spawners in this Cobblemon version, skipping them")
        }
    }

    private fun installFishingAndSnacks() {
        com.cobblemon.mod.common.api.spawning.spawner.FishingSpawnerFactory.positionInfluenceBuilders.add { listOf(FinalStageInfluence) }
        com.cobblemon.mod.common.api.spawning.spawner.PokeSnackSpawnerFactory.influenceBuilders.add { FinalStageInfluence }
    }
}
