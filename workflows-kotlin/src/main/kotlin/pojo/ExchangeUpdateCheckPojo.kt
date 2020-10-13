package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class ExchangeUpdateCheckPojo(
        val exchangeCode: String = "",
        val snamCheck: String = "",
        val financialCheck: String =""
)