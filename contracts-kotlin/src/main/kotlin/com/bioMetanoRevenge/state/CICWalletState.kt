package com.bioMetanoRevenge.state

import com.bioMetanoRevenge.contract.CICWalletContract
import com.bioMetanoRevenge.schema.CICWalletSchemaV1
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant


@BelongsToContract(CICWalletContract::class)
data class CICWalletState(val GSE: Party,
                          val owner: Party,
                          val CICWalletID: String,
                          val CICAmount: Double,
                          val CICWalletDate: Instant,
                          val CICWalletLastUpdate: Instant,
                          override val linearId: UniqueIdentifier = UniqueIdentifier()) :
        LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(GSE, owner)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is CICWalletSchemaV1 -> CICWalletSchemaV1.PersistentCICWallet(
                    this.GSE.name.toString(),
                    this.owner.name.toString(),
                    this.CICWalletID,
                    this.CICAmount,
                    this.CICWalletDate,
                    this.CICWalletLastUpdate,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(CICWalletSchemaV1)
}