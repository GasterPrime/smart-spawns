/*
 * Copyright (c) 2026 GasterPrime
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gasterprime.smartspawns

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object SmartSpawns : ModInitializer {
    const val MOD_ID = "smartspawns"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        // Spawn rules are ported in once the original PCLSpawns logic is reviewed.
        LOGGER.info("Smart Spawns loaded")
    }
}
