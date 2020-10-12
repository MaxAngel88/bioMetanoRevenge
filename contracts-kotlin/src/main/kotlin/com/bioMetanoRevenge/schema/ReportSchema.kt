package com.bioMetanoRevenge.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for ReportState.
 */
object ReportSchema

/**
 * An ReportState schema.
 */
object ReportSchemaV1 : MappedSchema(
        schemaFamily = ReportSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentReport::class.java)) {

    @Entity
    @Table(name = "report_states")
    class PersistentReport(
            @Column(name = "GSE")
            var GSE: String,

            @Column(name = "Snam")
            var Snam: String,

            @Column(name = "owner")
            var owner: String,

            @Column(name = "ownerID")
            var ownerID: String,

            @Column(name = "reportID")
            var reportID: String,

            @Column(name = "reportType")
            var reportType: String,

            @Column(name = "remiCode")
            var remiCode: String,

            @Column(name = "remiAddress")
            var remiAddress: String,

            @Column(name = "measuredQuantity")
            var measuredQuantity: Double,

            @Column(name = "measuredEnergy")
            var measuredEnergy: Double,

            @Column(name = "measuredPcs")
            var measuredPcs: Double,

            @Column(name = "measuredPci")
            var measuredPci: Double,

            @Column(name = "reportDate")
            var reportDate: Instant,

            @Column(name = "reportLastUpdate")
            var reportLastUpdate: Instant,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this(
                GSE = "",
                Snam = "",
                owner = "",
                ownerID = "",
                reportID = "",
                reportType = "",
                remiCode = "",
                remiAddress = "",
                measuredQuantity= 0.0,
                measuredEnergy = 0.0,
                measuredPcs = 0.0,
                measuredPci = 0.0,
                reportDate = Instant.now(),
                reportLastUpdate = Instant.now(),
                linearId = UUID.randomUUID()
        )
    }
}