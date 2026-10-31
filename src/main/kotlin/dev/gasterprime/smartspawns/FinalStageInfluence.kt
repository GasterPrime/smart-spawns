/*
 * Copyright (c) 2026 GasterPrime
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gasterprime.smartspawns

import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition

/**
 * Second line of the final-stage rule, for spawns that never go through the world spawn pool.
 *
 * Cobblemon 1.8 habitats inject their own spawn entries at spawn time (for example an Excadrill habitat
 * near a Drilbur area). Those entries skip the pool, so [FinalStageFilter] cannot remove them, but every
 * candidate is still weighed by the spawner's influences. A blocked entry gets weight 0 and is never picked.
 */
object FinalStageInfluence : SpawningInfluence {
    override fun affectWeight(detail: SpawnDetail, spawnablePosition: SpawnablePosition, weight: Float): Float =
        if (weight > 0f && FinalStageFilter.isBlocked(detail)) 0f else weight
}
