package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class ReportPojo(
        val ownerNode: String = "",
        val ownerID: String = "",
        val reportID: String = "",
        val reportType: String = "",
        val remiCode: String  = "",
        val remiAddress: String = "",
        val measuredQuantity: Double = 0.0,
        val measuredEnergy: Double = 0.0,
        val measuredPcs: Double = 0.0,
        val measuredPci: Double = 0.0
)