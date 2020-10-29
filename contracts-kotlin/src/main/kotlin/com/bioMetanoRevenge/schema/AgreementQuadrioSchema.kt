package com.bioMetanoRevenge.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for AgreementQuadrioState.
 */
object AgreementQuadrioSchema

/**
 * An AgreementQuadrioState schema.
 */
object AgreementQuadrioSchemaV1 : MappedSchema(
        schemaFamily = AgreementQuadrioSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentAgreementQuadrio::class.java)) {

    @Entity
    @Table(name = "agreement_quadrio_states")
    class PersistentAgreementQuadrio(
            @Column(name = "GSE")
            var GSE: String,

            @Column(name = "ownerParty")
            var ownerParty: String,

            @Column(name = "counterpartParty")
            var counterpartParty: String,

            @Column(name = "agreementQuadrioID")
            var agreementQuadrioID: String,

            @Column(name = "agreementQuadrioCode")
            var agreementQuadrioCode: String,

            @Column(name = "agreementQuadrioType")
            var agreementQuadrioType: String,

            @Column(name = "owner")
            var owner: String,

            @Column(name = "counterpart")
            var counterpart: String,

            @Column(name = "remiCode")
            var remiCode: String,

            @Column(name = "yearRef")
            var yearRef: String,

            @Column(name = "supportField")
            var supportField: String,

            @Column(name = "yearQuantity")
            var yearQuantity: Double,

            @Column(name = "validFrom")
            var validFrom: Instant,

            @Column(name = "validTo")
            var validTo: Instant,

            @Column(name = "agreementQuadrioStatus")
            var agreementQuadrioStatus: String,

            @Column(name = "agreementQuadrioDate")
            var agreementQuadrioDate: Instant,

            @Column(name = "agreementQuadrioLastUpdate")
            var agreementQuadrioLastUpdate: Instant,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this(
                GSE = "",
                ownerParty = "",
                counterpartParty = "",
                agreementQuadrioID = "",
                agreementQuadrioCode = "",
                agreementQuadrioType = "",
                owner = "",
                counterpart = "",
                remiCode = "",
                yearRef = "",
                supportField = "",
                yearQuantity = 0.0,
                validFrom = Instant.now(),
                validTo = Instant.now(),
                agreementQuadrioStatus = "",
                agreementQuadrioDate = Instant.now(),
                agreementQuadrioLastUpdate = Instant.now(),
                linearId = UUID.randomUUID()
        )
    }
}