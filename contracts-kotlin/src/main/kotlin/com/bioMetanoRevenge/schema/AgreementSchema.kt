package com.bioMetanoRevenge.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for AgreementState.
 */
object AgreementSchema

/**
 * An AgreementState schema.
 */
object AgreementSchemaV1 : MappedSchema(
        schemaFamily = AgreementSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentAgreement::class.java)) {

    @Entity
    @Table(name = "agreement_states")
    class PersistentAgreement(
            @Column(name = "GSE")
            var GSE: String,

            @Column(name = "ownerParty")
            var ownerParty: String,

            @Column(name = "counterpartParty")
            var counterpartParty: String,

            @Column(name = "agreementID")
            var agreementID: String,

            @Column(name = "agreementCode")
            var agreementCode: String,

            @Column(name = "agreementDescription")
            var agreementDescription: String,

            @Column(name = "agreementType")
            var agreementType: String,

            @Column(name = "agreementSubType")
            var agreementSubType: String,

            @Column(name = "owner")
            var owner: String,

            @Column(name = "counterpart")
            var counterpart: String,

            @Column(name = "tso")
            var tso: String,

            @Column(name = "portfolio")
            var portfolio: String,

            @Column(name = "sourceSystem")
            var sourceSystem: String,

            @Column(name = "programmingMethod")
            var programmingMethod: String,

            @Column(name = "agreementNote")
            var agreementNote: String,

            @Column(name = "energy")
            var energy: Double,

            @Column(name = "dcq")
            var dcq: Double,

            @Column(name = "price")
            var price: Double,

            @Column(name = "validFrom")
            var validFrom: Instant,

            @Column(name = "validTo")
            var validTo: Instant,

            @Column(name = "agreementDate")
            var agreementDate: Instant,

            @Column(name = "agreementLastUpdate")
            var agreementLastUpdate: Instant,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this(GSE = "", ownerParty = "", counterpartParty = "", agreementID = "", agreementCode = "", agreementDescription = "", agreementType = "", agreementSubType = "", owner = "", counterpart = "", tso = "", portfolio = "", sourceSystem = "", programmingMethod = "", agreementNote = "", energy = 0.0, dcq = 0.0, price = 0.0, validFrom = Instant.now(), validTo = Instant.now(), agreementDate = Instant.now(), agreementLastUpdate = Instant.now(), linearId = UUID.randomUUID())
    }
}