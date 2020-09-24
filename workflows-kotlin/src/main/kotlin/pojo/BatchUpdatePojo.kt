package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class BatchUpdatePojo(
        val batchID: String = "",
        val batchStatus: String = "",
        val quantity: Double = 0.0
)