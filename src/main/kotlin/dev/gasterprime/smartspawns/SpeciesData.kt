/*
 * Copyright (c) 2026 GasterPrime
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gasterprime.smartspawns

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.requirements.LevelRequirement
import net.minecraft.resources.ResourceLocation
import java.util.Locale

/** Species and evolution lookups, worked out from Cobblemon's own data so addon species are covered too. */
object SpeciesData {

    fun species(raw: String?): Species? {
        if (raw.isNullOrBlank() || raw.equals("random", ignoreCase = true)) return null
        return runCatching {
            val name = raw.trim().lowercase(Locale.ROOT)
            if (":" in name) PokemonSpecies.getByIdentifier(ResourceLocation.parse(name)) else PokemonSpecies.getByName(name)
        }.getOrNull()
    }

    /** The form a spawn entry asks for through its aspects (alolan, hisuian...), or the standard form. */
    fun form(species: Species, aspects: Set<String>?): FormData =
        runCatching { if (aspects.isNullOrEmpty()) species.standardForm else species.getForm(aspects) }
            .getOrDefault(species.standardForm)

    /** Has a pre-evolution and nothing left to evolve into. Single-stage Pokémon such as Tauros are not final stages. */
    fun isFinalStage(form: FormData): Boolean =
        runCatching { form.preEvolution != null && form.evolutions.isEmpty() }.getOrDefault(false)

    /** The level a Pokémon needs to evolve into [form], or null when that evolution is not level based. */
    fun evolutionLevel(form: FormData): Int? = runCatching {
        val pre = form.preEvolution ?: return null
        val target = form.species.resourceIdentifier.path
        pre.form.evolutions
            .filter { evolution -> evolution.result.species?.substringAfter(':')?.equals(target, ignoreCase = true) == true }
            .flatMap { evolution -> evolution.requirements.filterIsInstance<LevelRequirement>() }
            .minOfOrNull { it.minLevel }
    }.getOrNull()

    fun isStandard(form: FormData): Boolean = form.name.equals("normal", ignoreCase = true)

    fun displayName(species: Species, form: FormData): String =
        if (isStandard(form)) species.name else "${species.name} (${form.name})"

    /** Key used to compare species, e.g. "vulpix" or "vulpix-alola" when forms are kept apart. */
    fun key(species: Species, form: FormData, separateForms: Boolean): String {
        val base = species.resourceIdentifier.path.lowercase(Locale.ROOT)
        return if (separateForms && !isStandard(form)) "$base-${form.name.lowercase(Locale.ROOT)}" else base
    }

    fun key(props: PokemonProperties?, separateForms: Boolean): String? {
        val species = species(props?.species) ?: return null
        return key(species, form(species, props?.aspects), separateForms)
    }

    /** Lower-cased species paths, with any namespace dropped. */
    fun names(list: List<String>): Set<String> =
        list.asSequence()
            .filter { it.isNotBlank() }
            .map { it.trim().lowercase(Locale.ROOT).substringAfter(':') }
            .toSet()
}
