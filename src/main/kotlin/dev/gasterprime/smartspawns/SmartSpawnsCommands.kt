/*
 * Copyright (c) 2026 GasterPrime
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gasterprime.smartspawns

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component

/** /smartspawns [status | reload | check <species> [form]], for operators and the console. */
object SmartSpawnsCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("smartspawns")
                .requires { it.hasPermission(2) }
                .executes { status(it.source) }
                .then(Commands.literal("status").executes { status(it.source) })
                .then(Commands.literal("reload").executes { reload(it.source) })
                .then(
                    Commands.literal("check").then(
                        Commands.argument("species", StringArgumentType.word())
                            .suggests { _, builder ->
                                SharedSuggestionProvider.suggest(PokemonSpecies.species.map { it.resourceIdentifier.path }, builder)
                            }
                            .executes { check(it, null) }
                            .then(
                                Commands.argument("form", StringArgumentType.word())
                                    .executes { check(it, StringArgumentType.getString(it, "form")) },
                            ),
                    ),
                ),
        )
    }

    private fun status(source: CommandSourceStack): Int {
        val config = SmartSpawns.config
        val rules = config.antiCluster
        val cobblemon = FabricLoader.getInstance().getModContainer("cobblemon")
            .map { it.metadata.version.friendlyString }.orElse("?")
        source.send("Smart Spawns ${SmartSpawns.version} on Cobblemon $cobblemon", ChatFormatting.GOLD)
        source.send(
            if (config.blockFinalEvolutions) {
                "Fully evolved Pokémon: kept out of the wild (${FinalStageFilter.blockedNames.size} species)"
            } else {
                "Fully evolved Pokémon: allowed in the wild"
            },
        )
        source.send("Spawn entries removed: ${FinalStageFilter.removedCount} of ${FinalStageFilter.totalCount}, herds trimmed: ${FinalStageFilter.herdsChanged}")
        source.send(
            if (rules.enabled) {
                "Anti-cluster: on, radius ${rules.radius}/${rules.verticalRadius}, max ${rules.maxNearby} nearby, " +
                    "weight x${rules.weightPerNearby} per match, ${AntiClusterInfluence.plannedCount} recent spawns remembered"
            } else {
                "Anti-cluster: off"
            },
        )
        source.send("Raise underleveled evolutions: ${if (config.underleveledEvolutions.raiseToEvolutionLevel) "on" else "off"}")
        return 1
    }

    private fun reload(source: CommandSourceStack): Int {
        SmartSpawns.reload()
        source.send("Smart Spawns reloaded. ${FinalStageFilter.blockedNames.size} species kept out of the wild.", ChatFormatting.GREEN)
        return 1
    }

    private fun check(context: CommandContext<CommandSourceStack>, aspect: String?): Int {
        val source = context.source
        val raw = StringArgumentType.getString(context, "species")
        val species = SpeciesData.species(raw)
        if (species == null) {
            source.send("Unknown species: $raw", ChatFormatting.RED)
            return 0
        }
        val form = SpeciesData.form(species, aspect?.let { setOf(it) })
        val name = SpeciesData.displayName(species, form)
        val pre = form.preEvolution?.let { SpeciesData.displayName(it.species, it.form) } ?: "none"
        val evolutions = form.evolutions.size
        val level = SpeciesData.evolutionLevel(form)

        source.send(name, ChatFormatting.GOLD)
        source.send("Evolves from: $pre" + (level?.let { " (at level $it)" } ?: ""))
        source.send("Evolves into: " + if (evolutions == 0) "nothing" else "$evolutions option(s)")
        source.send("Final stage: " + if (SpeciesData.isFinalStage(form)) "yes" else "no")
        val blocked = name in FinalStageFilter.blockedNames
        source.send(
            "In the wild: " + if (blocked) "kept out" else "can spawn",
            if (blocked) ChatFormatting.RED else ChatFormatting.GREEN,
        )
        return 1
    }

    private fun CommandSourceStack.send(text: String, color: ChatFormatting = ChatFormatting.GRAY) {
        sendSuccess({ Component.literal(text).withStyle(color) }, false)
    }
}
