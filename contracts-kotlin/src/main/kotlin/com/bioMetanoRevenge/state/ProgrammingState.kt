package com.bioMetanoRevenge.state

import com.bioMetanoRevenge.contract.ProgrammingContract
import com.bioMetanoRevenge.schema.ProgrammingSchemaV1
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant


@BelongsToContract(ProgrammingContract::class)
data class ProgrammingState(val GSE: Party,
                            val produttore: Party,
                            val sendDate: String,
                            val monthYear: String,
                            val programmingType: String,
                            val versionFile: String,
                            val bioAgreementCode: String,
                            val remiCode: String,
                            val docRef: String,
                            val docName: String,
                            val programmingDate: Instant,
                            override val linearId: UniqueIdentifier = UniqueIdentifier()):
        LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(GSE, produttore)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is ProgrammingSchemaV1 -> ProgrammingSchemaV1.PersistentProgramming(
                    this.GSE.name.toString(),
                    this.produttore.name.toString(),
                    this.sendDate,
                    this.monthYear,
                    this.programmingType,
                    this.versionFile,
                    this.bioAgreementCode,
                    this.remiCode,
                    this.docRef,
                    this.docName,
                    this.programmingDate,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ProgrammingSchemaV1)
}