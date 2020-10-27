package com.bioMetanoRevenge.state

import com.bioMetanoRevenge.contract.ReportContract
import com.bioMetanoRevenge.schema.ReportSchemaV1
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant


@BelongsToContract(ReportContract::class)
data class ReportState(val GSE: Party,
                       val Snam: Party,
                       val owner: Party,
                       val ownerID: String,
                       val reportID: String,
                       val reportType: String,
                       val remiCode: String,
                       val remiAddress: String,
                       val offerCode: String,
                       val transactionCode: String,
                       val operation: String,
                       val measuredQuantity: Double,
                       val measuredEnergy: Double,
                       val measuredPcs: Double,
                       val measuredPci: Double,
                       val reportDate: Instant,
                       val reportLastUpdate: Instant,
                       override val linearId: UniqueIdentifier = UniqueIdentifier()) :
        LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(GSE, Snam, owner)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is ReportSchemaV1 -> ReportSchemaV1.PersistentReport(
                    this.GSE.name.toString(),
                    this.Snam.name.toString(),
                    this.owner.name.toString(),
                    this.ownerID,
                    this.reportID,
                    this.reportType,
                    this.remiCode,
                    this.remiAddress,
                    this.offerCode,
                    this.transactionCode,
                    this.operation,
                    this.measuredQuantity,
                    this.measuredEnergy,
                    this.measuredPcs,
                    this.measuredPci,
                    this.reportDate,
                    this.reportLastUpdate,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ReportSchemaV1)
}