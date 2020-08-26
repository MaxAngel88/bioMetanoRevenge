package com.bioMetanoRevenge.state

import com.bioMetanoRevenge.contract.EnrollContract
import com.bioMetanoRevenge.schema.EnrollSchemaV1
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant


@BelongsToContract(EnrollContract::class)
data class EnrollState(val GSE: Party,
                       val owner: Party,
                       val enrollType: String,
                       val businessName: String,
                       val PIVA: String,
                       val birthPlace: String,
                       val idPlant: String,
                       val plantAddress: String,
                       val username: String,
                       val role: String,
                       val partner: String,
                       val docRefAutodichiarazione: String,
                       val docRefAttestazioniTecniche: String,
                       val docDeadLine: String,
                       val enrollStatus: String,
                       val enrollDate: Instant,
                       val bioGasAmount: Double,
                       val gasAmount: Double,
                       val uuid: String,
                       val lastUpdate: Instant,
                       override val linearId: UniqueIdentifier = UniqueIdentifier()):
        LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(GSE, owner)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is EnrollSchemaV1 -> EnrollSchemaV1.PersistentEnroll(
                    this.GSE.name.toString(),
                    this.owner.name.toString(),
                    this.enrollType,
                    this.businessName,
                    this.PIVA,
                    this.birthPlace,
                    this.idPlant,
                    this.plantAddress,
                    this.username,
                    this.role,
                    this.partner,
                    this.docRefAutodichiarazione,
                    this.docRefAttestazioniTecniche,
                    this.docDeadLine,
                    this.enrollStatus,
                    this.enrollDate,
                    this.bioGasAmount,
                    this.gasAmount,
                    this.uuid,
                    this.lastUpdate,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(EnrollSchemaV1)
}