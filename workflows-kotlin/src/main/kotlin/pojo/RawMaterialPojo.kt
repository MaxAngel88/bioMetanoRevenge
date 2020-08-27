package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class RawMaterialPojo(
        val rawMaterialChar: String = "",
        val rawMaterialType: String = "",
        val CERCode: String = "",
        val quantity: Double = 0.0,
        val originCountry: String = "",
        val secondaryHarvest: Boolean = false,
        val degradedLand: Boolean = false,
        val sustainabilityCode: String = ""
)