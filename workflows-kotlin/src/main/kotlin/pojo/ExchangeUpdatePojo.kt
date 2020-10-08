package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class ExchangeUpdatePojo(
        val exchangeCode: String = "",
        val exchangeStatus: String = "",
        val sellingQuantity: Double = 0.0,
        val sellingPrice: Double = 0.0
)