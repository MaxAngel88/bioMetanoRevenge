package com.bioMetanoRevenge.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for PSVState.
 */
object PSVSchema

/**
 * An PSVState schema.
 */
object PSVSchemaV1 : MappedSchema(
        schemaFamily = PSVSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentPSV::class.java)) {

    @Entity
    @Table(name = "psv_states")
    class PersistentPSV(
            @Column(name = "GSE")
            var GSE: String,

            @Column(name= "Snam")
            var Snam: String,

            @Column(name = "seller")
            var seller: String,

            @Column(name = "buyer")
            var buyer: String,

            @Column(name = "exchangeType")
            var transactionType: String,

            @Column(name = "PIVASeller")
            var PIVASeller: String,

            @Column(name = "PIVABuyer")
            var PIVABuyer: String,

            @Column(name = "parentBatchID")
            var parentBatchID: String,

            @Column(name = "exchangeCode")
            var transactionCode: String,

            @Column(name = "month")
            var month: String,

            @Column(name = "remiCode")
            var remiCode: String,

            @Column(name = "plantAddress")
            var plantAddress: String,

            @Column(name = "plantCode")
            var plantCode: String,

            @Column(name = "initialQuantity")
            var initialQuantity: Double,

            @Column(name = "quantity")
            var quantity: Double,

            @Column(name = "price")
            var price: Double,

            @Column(name = "pcs")
            var pcs: Double,

            @Column(name = "pci")
            var pci: Double,

            @Column(name = "docRef")
            var docRef: String,

            @Column(name = "docDate")
            var docDate: String,

            @Column(name = "transactionStatus")
            var transactionStatus: String,

            @Column(name = "transactionDate")
            var exchangeDate: Instant,

            @Column(name = "transactionLastUpdate")
            var lastExchangeUpdate: Instant,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this(GSE = "", Snam = "", seller = "", buyer = "", transactionType = "", PIVASeller = "", PIVABuyer = "", parentBatchID = "", transactionCode = "", month = "", remiCode = "", plantAddress = "", plantCode = "", initialQuantity = 0.0, quantity = 0.0, price = 0.0, pcs = 0.0, pci = 0.0, docRef = "", docDate = "", transactionStatus = "", exchangeDate = Instant.now(), lastExchangeUpdate = Instant.now(), linearId = UUID.randomUUID())
    }
}