package com.bioMetanoRevenge.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for EnrollState.
 */
object EnrollSchema

/**
 * An EnrollState schema.
 */
object EnrollSchemaV1 : MappedSchema(
        schemaFamily = EnrollSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentEnroll::class.java)) {

    @Entity
    @Table(name = "enroll_states")
    class PersistentEnroll(
            @Column(name = "GSE")
            var GSE: String,

            @Column(name = "owner")
            var owner: String,

            @Column(name = "enrollType")
            var enrollType: String,

            @Column(name = "subjectFirstName")
            var subjectFirstName: String,

            @Column(name = "subjectLastName")
            var subjectLastName: String,

            @Column(name = "subjectAddress")
            var subjectAddress: String,

            @Column(name = "subjectBusiness")
            var subjectBusiness: String,

            @Column(name = "qualificationCode")
            var qualificationCode: String,

            @Column(name = "businessName")
            var businessName: String,

            @Column(name = "PIVA")
            var PIVA: String,

            @Column(name = "birthPlace")
            var birthPlace: String,

            @Column(name = "remiCode")
            var remiCode: String,

            @Column(name = "remiAddress")
            var remiAddress: String,

            @Column(name = "idPlant")
            var idPlant: String,

            @Column(name = "plantAddress")
            var plantAddress: String,

            @Column(name = "username")
            var username: String,

            @Column(name = "role")
            var role: String,

            @Column(name = "partner")
            var partner: String,

            @Column(name = "docRefAutodichiarazione")
            var docRefAutodichiarazione: String,

            @Column(name = "docRefAttestazioniTecniche")
            var docRefAttestazioniTecniche: String,

            @Column(name = "docDeadLineAuto")
            var docDeadLineAuto: String,

            @Column(name = "docDeadLineTech")
            var docDeadLineTech: String,

            @Column(name = "enrollStatus")
            var enrollStatus: String,

            @Column(name = "enrollDate")
            var enrollDate: Instant,

            @Column(name = "bioGasAmount")
            var bioGasAmount: Double,

            @Column(name = "gasAmount")
            var gasAmount: Double,

            @Column(name = "uuid")
            var uuid: String,

            @Column(name = "lastUpdate")
            var lastUpdate: Instant,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this(GSE = "", owner = "", enrollType = "", subjectFirstName = "", subjectLastName = "", subjectAddress = "", subjectBusiness = "", qualificationCode = "", businessName = "", PIVA = "", birthPlace = "", remiCode = "", remiAddress = "", idPlant = "", plantAddress = "", username = "", role = "", partner = "", docRefAutodichiarazione = "", docRefAttestazioniTecniche = "", docDeadLineAuto = "", docDeadLineTech = "", enrollStatus = "", enrollDate = Instant.now(), bioGasAmount = 0.0, gasAmount = 0.0, uuid = "", lastUpdate = Instant.now(), linearId = UUID.randomUUID())
    }
}