package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class ReportUpdatePojo(
        val reportID: String = "",
        val measuredQuantity: Double = 0.0,
        val measuredEnergy: Double = 0.0,
        val measuredPcs: Double = 0.0,
        val measuredPci: Double = 0.0
)