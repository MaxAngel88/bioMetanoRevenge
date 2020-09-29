package com.bioMetanoRevenge.state

import com.bioMetanoRevenge.contract.BatchContract
import com.bioMetanoRevenge.schema.BatchSchemaV1
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant


@BelongsToContract(BatchContract::class)
data class BatchState(val GSE: Party,
                      val Snam: Party,
                      val produttore: Party,
                      val shipper: Party,
                      val idProducer: String,
                      val idShipper: String,
                      val transactionType: String,
                      val batchID: String,
                      val month: String,
                      val remiCode: String,
                      val plantAddress: String,
                      val plantCode: String,
                      val initialQuantity: Double,
                      val quantity: Double,
                      val energy: Double,
                      val price: Double,
                      val averageProductionCapacity: Double,
                      val maxProductionCapacity: Double,
                      val annualEstimate: Double,
                      val pcs: Double,
                      val pci: Double,
                      val startingPosition: String,
                      val arrivalPosition: String,
                      val docRef: String,
                      val docDate: String,
                      val batchStatus: String,
                      val batchDate: Instant,
                      val lastBatchUpdate: Instant,
                      override val linearId: UniqueIdentifier = UniqueIdentifier()) :
        LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(GSE, Snam, produttore, shipper)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is BatchSchemaV1 -> BatchSchemaV1.PersistentBatch(
                    this.GSE.name.toString(),
                    this.Snam.name.toString(),
                    this.produttore.name.toString(),
                    this.shipper.name.toString(),
                    this.idProducer,
                    this.idShipper,
                    this.transactionType,
                    this.batchID,
                    this.month,
                    this.remiCode,
                    this.plantAddress,
                    this.plantCode,
                    this.initialQuantity,
                    this.quantity,
                    this.energy,
                    this.price,
                    this.averageProductionCapacity,
                    this.maxProductionCapacity,
                    this.annualEstimate,
                    this.pcs,
                    this.pci,
                    this.startingPosition,
                    this.arrivalPosition,
                    this.docRef,
                    this.docDate,
                    this.batchStatus,
                    this.batchDate,
                    this.lastBatchUpdate,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(BatchSchemaV1)
}