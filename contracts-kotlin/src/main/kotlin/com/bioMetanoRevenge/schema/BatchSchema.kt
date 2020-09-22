package com.bioMetanoRevenge.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for BatchState.
 */
object BatchSchema

/**
 * An BatchState schema.
 */
object BatchSchemaV1 : MappedSchema(
        schemaFamily = BatchSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentBatch::class.java)) {

    @Entity
    @Table(name = "batch_states")
    class PersistentBatch(
            @Column(name = "GSE")
            var GSE: String,

            @Column(name = "Snam")
            var Snam: String,

            @Column(name = "produttore")
            var produttore: String,

            @Column(name = "shipper")
            var shipper: String,

            @Column(name = "idProducer")
            var idProducer: String,

            @Column(name = "idShipper")
            var idShipper: String,

            @Column(name = "transactionType")
            var transactionType: String,

            @Column(name = "batchID")
            var batchID: String,

            @Column(name = "month")
            var month: String,

            @Column(name = "remiCode")
            var remiCode: String,

            @Column(name = "plantAddress")
            var plantAddress: String,

            @Column(name = "plantCode")
            var plantCode: String,

            @Column(name = "quantity")
            var quantity: Double,

            @Column(name = "energy")
            var energy: Double,

            @Column(name = "price")
            var price: Double,

            @Column(name = "averageProductionCapacity")
            var averageProductionCapacity: Double,

            @Column(name = "maxProductionCapacity")
            var maxProductionCapacity: Double,

            @Column(name = "annualEstimate")
            var annualEstimate: Double,

            @Column(name = "startingPosition")
            var startingPosition: String,

            @Column(name = "arrivalPosition")
            var arrivalPosition: String,

            @Column(name = "docRef")
            var docRef: String,

            @Column(name = "docDate")
            var docDate: String,

            @Column(name = "batchStatus")
            var batchStatus: String,

            @Column(name = "batchDate")
            var batchDate: Instant,

            @Column(name = "lastBatchUpdate")
            var lastBatchUpdate: Instant,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this(GSE = "", Snam = "", produttore = "", shipper = "", idProducer = "", idShipper = "", transactionType = "", batchID = "", month = "", remiCode = "", plantAddress = "", plantCode = "", quantity = 0.0, energy = 0.0, price = 0.0, averageProductionCapacity = 0.0, maxProductionCapacity = 0.0, annualEstimate = 0.0, startingPosition = "", arrivalPosition = "", docRef = "", docDate = "", batchStatus = "", batchDate = Instant.now(), lastBatchUpdate = Instant.now() , linearId = UUID.randomUUID())
    }
}