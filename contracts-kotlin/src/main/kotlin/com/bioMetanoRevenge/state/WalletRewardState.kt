package com.bioMetanoRevenge.state

import com.bioMetanoRevenge.contract.WalletRewardContract
import com.bioMetanoRevenge.schema.WalletRewardSchemaV1
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant


@BelongsToContract(WalletRewardContract::class)
data class WalletRewardState(val GSE: Party,
                             val owner: Party,
                             val walletID: String,
                             val rewardPoint: Int,
                             val reason: String,
                             val walletDate: Instant,
                             val walletLastUpdate: Instant,
                             override val linearId: UniqueIdentifier = UniqueIdentifier()) :
        LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(GSE, owner)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is WalletRewardSchemaV1 -> WalletRewardSchemaV1.PersistentWalletReward(
                    this.GSE.name.toString(),
                    this.owner.name.toString(),
                    this.walletID,
                    this.rewardPoint,
                    this.reason,
                    this.walletDate,
                    this.walletLastUpdate,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(WalletRewardSchemaV1)
}