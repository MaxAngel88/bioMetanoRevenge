package com.bioMetanoRevenge.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for WalletRewardState.
 */
object WalletRewardSchema

/**
 * An WalletRewardState schema.
 */
object WalletRewardSchemaV1 : MappedSchema(
        schemaFamily = WalletRewardSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentWalletReward::class.java)) {

    @Entity
    @Table(name = "wallet_reward_states")
    class PersistentWalletReward(
            @Column(name = "GSE")
            var GSE: String,

            @Column(name = "owner")
            var owner: String,

            @Column(name = "walletID")
            var walletID: String,

            @Column(name = "rewardPoint")
            var rewardPoint: Int,

            @Column(name = "reason")
            var reason: String,

            @Column(name = "walletDate")
            var walletDate: Instant,

            @Column(name = "walletLastUpdate")
            var walletLastUpdate: Instant,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this(
                GSE = "",
                owner = "",
                walletID = "",
                rewardPoint = 0,
                reason = "",
                walletDate = Instant.now(),
                walletLastUpdate = Instant.now(),
                linearId = UUID.randomUUID()
        )
    }
}