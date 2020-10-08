package com.bioMetanoRevenge.state

import com.bioMetanoRevenge.contract.AgreementContract
import com.bioMetanoRevenge.schema.AgreementSchemaV1
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant


@BelongsToContract(AgreementContract::class)
data class AgreementState(val GSE: Party,
                          val ownerParty: Party,
                          val counterpartParty: Party,
                          val agreementID: String,
                          val agreementCode: String,
                          val agreementDescription: String,
                          val agreementType: String,
                          val agreementSubType: String,
                          val plantID: String,
                          val plantAddress: String,
                          val plantBusinessName: String,
                          val owner: String,
                          val counterpart: String,
                          val remiCode: String,
                          val averageDailyCapacity: Double,
                          val maxDailyCapacity: Double,
                          val energyEstimation: Double,
                          val tso: String,
                          val portfolio: String,
                          val sourceSystem: String,
                          val programmingMethod: String,
                          val agreementNote: String,
                          val invoicingCode: String,
                          val energy: Double,
                          val dcq: Double,
                          val price: Double,
                          val validFrom: Instant,
                          val validTo: Instant,
                          val agreementStatus: String,
                          val agreementDate: Instant,
                          val agreementLastUpdate: Instant,
                          override val linearId: UniqueIdentifier = UniqueIdentifier()) :
        LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(GSE, ownerParty, counterpartParty)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is AgreementSchemaV1 -> AgreementSchemaV1.PersistentAgreement(
                    this.GSE.name.toString(),
                    this.ownerParty.name.toString(),
                    this.counterpartParty.name.toString(),
                    this.agreementID,
                    this.agreementCode,
                    this.agreementDescription,
                    this.agreementType,
                    this.agreementSubType,
                    this.plantID,
                    this.plantAddress,
                    this.plantBusinessName,
                    this.owner,
                    this.counterpart,
                    this.remiCode,
                    this.averageDailyCapacity,
                    this.maxDailyCapacity,
                    this.energyEstimation,
                    this.tso,
                    this.portfolio,
                    this.sourceSystem,
                    this.programmingMethod,
                    this.agreementNote,
                    this.invoicingCode,
                    this.energy,
                    this.dcq,
                    this.price,
                    this.validFrom,
                    this.validTo,
                    this.agreementStatus,
                    this.agreementDate,
                    this.agreementLastUpdate,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(AgreementSchemaV1)
}