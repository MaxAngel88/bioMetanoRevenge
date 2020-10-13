package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class PSVUpdateCheckPojo(
        val transactionCode: String = "",
        val snamCheck: String = "",
        val financialCheck: String =""
)