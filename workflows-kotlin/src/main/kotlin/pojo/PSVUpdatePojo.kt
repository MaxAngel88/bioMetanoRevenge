package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class PSVUpdatePojo(
        val transactionCode: String = "",
        val transactionStatus: String = "",
        val sellingQuantity: Double = 0.0,
        val sellingPrice: Double = 0.0
)