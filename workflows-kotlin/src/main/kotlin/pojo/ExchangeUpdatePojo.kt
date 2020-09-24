package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class ExchangeUpdatePojo(
        val exchangeCode: String = "",
        val exchangeStatus: String = "",
        val quantity: Double = 0.0
)