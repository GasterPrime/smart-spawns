/*
 * Copyright (c) 2026 GasterPrime
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.gasterprime.smartspawns

import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail
import java.lang.reflect.Method

/**
 * Leader/follower flags on herd members, for both Cobblemon lines on Minecraft 1.21.1.
 *
 * 1.8.x has `isLeader: Boolean` and `isFollower: Boolean`. 1.7.x has `isLeader: Boolean?` and no follower flag.
 * Calling either compiled signature directly would crash on the other version, so the accessors are looked up once.
 */
object HerdRoles {
    private val type = PokemonHerdSpawnDetail.Herdable::class.java
    private val getLeader: Method = type.getMethod("isLeader")
    private val setLeader: Method = type.methods.first { it.name == "setLeader" && it.parameterCount == 1 }
    private val getFollower: Method? = runCatching { type.getMethod("isFollower") }.getOrNull()
    private val setFollower: Method? = type.methods.firstOrNull { it.name == "setFollower" && it.parameterCount == 1 }

    data class Snapshot(val leader: Any?, val follower: Any?)

    fun isLeader(member: PokemonHerdSpawnDetail.Herdable): Boolean = getLeader.invoke(member) == true

    fun snapshot(member: PokemonHerdSpawnDetail.Herdable) = Snapshot(getLeader.invoke(member), getFollower?.invoke(member))

    fun restore(member: PokemonHerdSpawnDetail.Herdable, snapshot: Snapshot) {
        setLeader.invoke(member, snapshot.leader)
        if (snapshot.follower != null) setFollower?.invoke(member, snapshot.follower)
    }

    /** Lets this member spawn on its own, without waiting for a herd leader. */
    fun makeFollower(member: PokemonHerdSpawnDetail.Herdable) {
        setLeader.invoke(member, false)
        setFollower?.invoke(member, true)
    }
}
