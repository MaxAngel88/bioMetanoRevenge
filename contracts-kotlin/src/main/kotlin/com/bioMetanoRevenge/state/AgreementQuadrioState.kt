package com.bioMetanoRevenge.state

import com.bioMetanoRevenge.contract.AgreementQuadrioContract
import com.bioMetanoRevenge.schema.AgreementQuadrioSchemaV1
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant


@BelongsToContract(AgreementQuadrioContract::class)
data class AgreementQuadrioState(val GSE: Party,
                                 val ownerParty: Party,
                                 val counterpartParty: Party,
                                 val agreementQuadrioID: String,
                                 val agreementQuadrioCode: String,
                                 val agreementQuadrioType: String,
                                 val owner: String,
                                 val counterpart: String,
                                 val remiCode: String,
                                 val yearRef: String,
                                 var supportField: String,
                                 val yearQuantity: Double,
                                 val validFrom: Instant,
                                 val validTo: Instant,
                                 val agreementQuadrioStatus: String,
                                 val agreementQuadrioDate: Instant,
                                 val agreementQuadrioLastUpdate: Instant,
                                 override val linearId: UniqueIdentifier = UniqueIdentifier()) :
        LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(GSE, ownerParty, counterpartParty)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is AgreementQuadrioSchemaV1 -> AgreementQuadrioSchemaV1.PersistentAgreementQuadrio(
                    this.GSE.name.toString(),
                    this.ownerParty.name.toString(),
                    this.counterpartParty.name.toString(),
                    this.agreementQuadrioID,
                    this.agreementQuadrioCode,
                    this.agreementQuadrioType,
                    this.owner,
                    this.counterpart,
                    this.remiCode,
                    this.yearRef,
                    this.supportField,
                    this.yearQuantity,
                    this.validFrom,
                    this.validTo,
                    this.agreementQuadrioStatus,
                    this.agreementQuadrioDate,
                    this.agreementQuadrioLastUpdate,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(AgreementQuadrioSchemaV1)
}