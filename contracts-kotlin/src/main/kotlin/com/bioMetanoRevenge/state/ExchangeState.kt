package com.bioMetanoRevenge.state

import com.bioMetanoRevenge.contract.ExchangeContract
import com.bioMetanoRevenge.schema.ExchangeSchemaV1
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant


@BelongsToContract(ExchangeContract::class)
data class ExchangeState(val GSE: Party,
                         val seller: Party,
                         val buyer: Party,
                         val exchangeType: String,
                         val PIVASeller: String,
                         val PIVABuyer: String,
                         val parentBatchID: String,
                         val exchangeCode: String,
                         val month: String,
                         val remiCode: String,
                         val plantAddress: String,
                         val plantCode: String,
                         val hauler: String,
                         val PIVAHauler: String,
                         val trackCode: String,
                         val pickupDate: String,
                         val deliveryDate: String,
                         val initialQuantity: Double,
                         val quantity: Double,
                         val price: Double,
                         val sellingPrice: Double,
                         val pcs: Double,
                         val pci: Double,
                         val startingPosition: String,
                         val arrivalPosition: String,
                         val docRef: String,
                         val docDate: String,
                         val exchangeStatus: String,
                         val supportField: String,
                         val auctionStatus: String,
                         val exchangeDate: Instant,
                         val lastExchangeUpdate: Instant,
                         override val linearId: UniqueIdentifier = UniqueIdentifier()) :
        LinearState, QueryableState {

    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(GSE, seller, buyer)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is ExchangeSchemaV1 -> ExchangeSchemaV1.PersistentExchange(
                    this.GSE.name.toString(),
                    this.seller.name.toString(),
                    this.buyer.name.toString(),
                    this.exchangeType,
                    this.PIVASeller,
                    this.PIVABuyer,
                    this.parentBatchID,
                    this.exchangeCode,
                    this.month,
                    this.remiCode,
                    this.plantAddress,
                    this.plantCode,
                    this.hauler,
                    this.PIVAHauler,
                    this.trackCode,
                    this.pickupDate,
                    this.deliveryDate,
                    this.initialQuantity,
                    this.quantity,
                    this.price,
                    this.sellingPrice,
                    this.pcs,
                    this.pci,
                    this.startingPosition,
                    this.arrivalPosition,
                    this.docRef,
                    this.docDate,
                    this.exchangeStatus,
                    this.supportField,
                    this.auctionStatus,
                    this.exchangeDate,
                    this.lastExchangeUpdate,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(ExchangeSchemaV1)
}