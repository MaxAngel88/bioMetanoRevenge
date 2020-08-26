package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class EnrollUpdatePojo(
        val uuid: String = "",
        val enrollStatus: String = "",
        val bioGasAmount: Double = 0.0,
        val gasAmount: Double = 0.0
)