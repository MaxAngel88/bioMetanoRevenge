package com.bioMetanoRevenge.state

import com.bioMetanoRevenge.contract.RawMaterialContract
import com.bioMetanoRevenge.schema.RawMaterialSchemaV1
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant

@BelongsToContract(RawMaterialContract::class)
data class RawMaterialState(val GSE: Party,
                            val produttore: Party,
                            val rawMaterialChar: String,
                            val rawMaterialType: String,
                            val CERCode: String,
                            val quantity: Double,
                            val originCountry: String,
                            val secondaryHarvest: Boolean,
                            val degradedLand: Boolean,
                            val sustainabilityCode: String,
                            val rawMaterialDate: Instant,
                            override val linearId: UniqueIdentifier = UniqueIdentifier()):
        LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(GSE, produttore)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is RawMaterialSchemaV1 -> RawMaterialSchemaV1.PersistentRawMaterial(
                    this.GSE.name.toString(),
                    this.produttore.name.toString(),
                    this.rawMaterialChar,
                    this.rawMaterialType,
                    this.CERCode,
                    this.quantity,
                    this.originCountry,
                    this.secondaryHarvest,
                    this.degradedLand,
                    this.sustainabilityCode,
                    this.rawMaterialDate,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(RawMaterialSchemaV1)
}