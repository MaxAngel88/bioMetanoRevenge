package com.bioMetanoRevenge.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for ExchangeState.
 */
object ExchangeSchema

/**
 * An ExchangeState schema.
 */
object ExchangeSchemaV1 : MappedSchema(
        schemaFamily = ExchangeSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentExchange::class.java)) {

    @Entity
    @Table(name = "exchange_states")
    class PersistentExchange(
            @Column(name = "GSE")
            var GSE: String,

            @Column(name = "seller")
            var seller: String,

            @Column(name = "buyer")
            var buyer: String,

            @Column(name = "exchangeType")
            var exchangeType: String,

            @Column(name = "PIVASeller")
            var PIVASeller: String,

            @Column(name = "PIVABuyer")
            var PIVABuyer: String,

            @Column(name = "parentBatchID")
            var parentBatchID: String,

            @Column(name = "exchangeCode")
            var exchangeCode: String,

            @Column(name = "month")
            var month: String,

            @Column(name = "remiCode")
            var remiCode: String,

            @Column(name = "plantAddress")
            var plantAddress: String,

            @Column(name = "plantCode")
            var plantCode: String,

            @Column(name = "hauler")
            var hauler: String,

            @Column(name = "PIVAHauler")
            var PIVAHauler: String,

            @Column(name = "trackCode")
            var trackCode: String,

            @Column(name = "pickupDate")
            var pickupDate: String,

            @Column(name = "deliveryDate")
            var deliveryDate: String,

            @Column(name = "initialQuantity")
            var initialQuantity: Double,

            @Column(name = "quantity")
            var quantity: Double,

            @Column(name = "price")
            var price: Double,

            @Column(name = "startingPosition")
            var startingPosition: String,

            @Column(name = "arrivalPosition")
            var arrivalPosition: String,

            @Column(name = "docRef")
            var docRef: String,

            @Column(name = "docDate")
            var docDate: String,

            @Column(name = "batchStatus")
            var exchangeStatus: String,

            @Column(name = "batchDate")
            var exchangeDate: Instant,

            @Column(name = "lastBatchUpdate")
            var lastExchangeUpdate: Instant,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this(GSE = "", seller = "", buyer = "", exchangeType = "", PIVASeller = "", PIVABuyer = "", parentBatchID = "", exchangeCode = "", month = "", remiCode = "", plantAddress = "", plantCode = "", hauler = "", PIVAHauler = "", trackCode = "", pickupDate = "", deliveryDate = "", initialQuantity = 0.0, quantity = 0.0, price = 0.0, startingPosition = "", arrivalPosition = "", docRef = "", docDate = "", exchangeStatus = "", exchangeDate = Instant.now(), lastExchangeUpdate = Instant.now(), linearId = UUID.randomUUID())
    }
}