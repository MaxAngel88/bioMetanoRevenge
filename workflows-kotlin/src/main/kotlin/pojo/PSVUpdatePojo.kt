package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class PSVUpdatePojo(
        val transactionCode: String = "",
        val transactionStatus: String = ""
)