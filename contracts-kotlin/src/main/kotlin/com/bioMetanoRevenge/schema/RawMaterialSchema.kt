package com.bioMetanoRevenge.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for RawMaterialState.
 */
object RawMaterialSchema

/**
 * An RawMaterialState schema.
 */
object RawMaterialSchemaV1 : MappedSchema(
        schemaFamily = RawMaterialSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentRawMaterial::class.java)) {

    @Entity
    @Table(name = "rawmaterial_states")
    class PersistentRawMaterial(
            @Column(name = "GSE")
            var GSE: String,

            @Column(name = "produttore")
            var produttore: String,

            @Column(name = "rawMaterialChar")
            var rawMaterialChar: String,

            @Column(name = "rawMaterialType")
            var rawMaterialType: String,

            @Column(name = "CERCode")
            var CERCode: String,

            @Column(name = "quantity")
            var quantity: Double,

            @Column(name = "originCountry")
            var originCountry: String,

            @Column(name = "secondaryHarvest")
            var secondaryHarvest: Boolean,

            @Column(name = "degradedLand")
            var degradedLand: Boolean,

            @Column(name = "sustainabilityCode")
            var sustainabilityCode: String,

            @Column(name = "rawMaterialDate")
            var rawMaterialDate: Instant,

            @Column(name = "linear_id")
            var linearId: UUID

    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this(
                GSE = "",
                produttore = "",
                rawMaterialChar = "",
                rawMaterialType = "",
                CERCode = "",
                quantity = 0.0,
                originCountry = "",
                secondaryHarvest = false,
                degradedLand = false,
                sustainabilityCode = "",
                rawMaterialDate = Instant.now(),
                linearId = UUID.randomUUID()
        )
    }
}