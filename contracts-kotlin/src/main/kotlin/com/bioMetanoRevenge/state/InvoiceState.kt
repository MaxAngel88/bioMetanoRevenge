package com.bioMetanoRevenge.state

import com.bioMetanoRevenge.contract.InvoiceContract
import com.bioMetanoRevenge.schema.InvoiceSchemaV1
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant


@BelongsToContract(InvoiceContract::class)
data class InvoiceState(val GSE: Party,
                        val ownerParty: Party,
                        val ownerID: String,
                        val invoiceID: String,
                        val invoiceRef: String,
                        val parentQuadrioID: String,
                        val unityPrice: Double,
                        val quantity: Double,
                        val totalPrice: Double,
                        val productType: String,
                        val invoiceDate: Instant,
                        override val linearId: UniqueIdentifier = UniqueIdentifier()) :
        LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(GSE, ownerParty)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is InvoiceSchemaV1 -> InvoiceSchemaV1.PersistentInvoice(
                    this.GSE.name.toString(),
                    this.ownerParty.name.toString(),
                    this.ownerID,
                    this.invoiceID,
                    this.invoiceRef,
                    this.parentQuadrioID,
                    this.unityPrice,
                    this.quantity,
                    this.totalPrice,
                    this.productType,
                    this.invoiceDate,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(InvoiceSchemaV1)
}