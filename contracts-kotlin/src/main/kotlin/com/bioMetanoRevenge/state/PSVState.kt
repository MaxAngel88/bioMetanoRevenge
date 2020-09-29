package com.bioMetanoRevenge.state

import com.bioMetanoRevenge.contract.PSVContract
import com.bioMetanoRevenge.schema.PSVSchemaV1
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant


@BelongsToContract(PSVContract::class)
data class PSVState(val GSE: Party,
                    val Snam: Party,
                    val seller: Party,
                    val buyer: Party,
                    val transactionType: String,
                    val PIVASeller: String,
                    val PIVABuyer: String,
                    val parentBatchID: String,
                    val transactionCode: String,
                    val month: String,
                    val remiCode: String,
                    val plantAddress: String,
                    val plantCode: String,
                    val initialQuantity: Double,
                    val quantity: Double,
                    val price: Double,
                    val pcs: Double,
                    val pci: Double,
                    val docRef: String,
                    val docDate: String,
                    val transactionStatus: String,
                    val transactionDate: Instant,
                    val transactionLastUpdate: Instant,
                    override val linearId: UniqueIdentifier = UniqueIdentifier()) :
        LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(GSE, Snam, seller, buyer)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is PSVSchemaV1 -> PSVSchemaV1.PersistentPSV(
                    this.GSE.name.toString(),
                    this.Snam.name.toString(),
                    this.seller.name.toString(),
                    this.buyer.name.toString(),
                    this.transactionType,
                    this.PIVASeller,
                    this.PIVABuyer,
                    this.parentBatchID,
                    this.transactionCode,
                    this.month,
                    this.remiCode,
                    this.plantAddress,
                    this.plantCode,
                    this.initialQuantity,
                    this.quantity,
                    this.price,
                    this.pcs,
                    this.pci,
                    this.docRef,
                    this.docDate,
                    this.transactionStatus,
                    this.transactionDate,
                    this.transactionLastUpdate,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(PSVSchemaV1)
}