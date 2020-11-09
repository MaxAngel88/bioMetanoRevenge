package com.bioMetanoRevenge.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for InvoiceState.
 */
object InvoiceSchema

/**
 * An InvoiceState schema.
 */
object InvoiceSchemaV1 : MappedSchema(
        schemaFamily = InvoiceSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentInvoice::class.java)) {

    @Entity
    @Table(name = "invoice_states")
    class PersistentInvoice(
            @Column(name = "GSE")
            var GSE: String,

            @Column(name = "ownerParty")
            var ownerParty: String,

            @Column(name = "ownerID")
            var ownerID: String,

            @Column(name = "invoiceID")
            var invoiceID: String,

            @Column(name = "invoiceRef")
            var invoiceRef: String,

            @Column(name = "parentQuadrioID")
            var parentQuadrioID: String,

            @Column(name = "unityPrice")
            var unityPrice: Double,

            @Column(name = "quantity")
            var quantity: Double,

            @Column(name = "totalPrice")
            var totalPrice: Double,

            @Column(name = "productType")
            var productType: String,

            @Column(name = "invoiceStatus")
            var invoiceStatus: String,

            @Column(name = "invoiceDate")
            var invoiceDate: Instant,

            @Column(name = "invoiceLastUpdate")
            var invoiceLastUpdate: Instant,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this(
                GSE = "",
                ownerParty = "" ,
                ownerID = "",
                invoiceID = "",
                invoiceRef = "",
                parentQuadrioID = "",
                unityPrice = 0.0,
                quantity = 0.0,
                totalPrice = 0.0,
                productType = "",
                invoiceStatus = "",
                invoiceDate = Instant.now(),
                invoiceLastUpdate = Instant.now(),
                linearId = UUID.randomUUID()
        )
    }
}