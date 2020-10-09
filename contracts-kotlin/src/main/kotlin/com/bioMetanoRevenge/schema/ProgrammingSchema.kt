package com.bioMetanoRevenge.schema


import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for ProgrammingState.
 */
object ProgrammingSchema

/**
 * An ProgrammingState schema.
 */
object ProgrammingSchemaV1 : MappedSchema(
        schemaFamily = ProgrammingSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentProgramming::class.java)) {

    @Entity
    @Table(name = "programming_states")
    class PersistentProgramming(
            @Column(name = "GSE")
            var GSE: String,

            @Column(name = "produttore")
            var produttore: String,

            @Column(name = "sendDate")
            var sendDate: String,

            @Column(name = "monthYear")
            var monthYear: String,

            @Column(name = "programmingType")
            var programmingType: String,

            @Column(name = "versionFile")
            var versionFile: String,

            @Column(name = "bioAgreementCode")
            var bioAgreementCode: String,

            @Column(name = "remiCode")
            var remiCode: String,

            @Column(name = "docRef")
            var docRef: String,

            @Column(name = "docName")
            var docName: String,

            @Column(name = "programmingStatus")
            var programmingStatus: String,

            @Column(name = "programmingDate")
            var programmingDate: Instant,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this(
                GSE = "",
                produttore = "",
                sendDate = "",
                monthYear = "",
                programmingType = "",
                versionFile = "",
                bioAgreementCode = "",
                remiCode = "",
                docRef = "",
                docName = "",
                programmingStatus = "",
                programmingDate = Instant.now(),
                linearId = UUID.randomUUID()
        )
    }
}