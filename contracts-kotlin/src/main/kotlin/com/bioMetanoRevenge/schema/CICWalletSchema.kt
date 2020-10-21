package com.bioMetanoRevenge.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for CICWalletState.
 */
object CICWalletSchema

/**
 * An CICWalletState schema.
 */
object CICWalletSchemaV1 : MappedSchema(
        schemaFamily = CICWalletSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentCICWallet::class.java)) {

    @Entity
    @Table(name = "cic_wallet_states")
    class PersistentCICWallet(
            @Column(name = "GSE")
            var GSE: String,

            @Column(name = "owner")
            var owner: String,

            @Column(name = "CICWalletID")
            var CICWalletID: String,

            @Column(name = "CICAmount")
            var CICAmount: Double,

            @Column(name = "CICWalletDate")
            var CICWalletDate: Instant,

            @Column(name = "CICWalletLastUpdate")
            var CICWalletLastUpdate: Instant,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this(
                GSE = "",
                owner = "",
                CICWalletID = "",
                CICAmount = 0.0,
                CICWalletDate = Instant.now(),
                CICWalletLastUpdate = Instant.now(),
                linearId = UUID.randomUUID()
        )
    }
}