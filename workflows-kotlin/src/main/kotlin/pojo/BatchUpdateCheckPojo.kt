package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class BatchUpdateCheckPojo(
        val batchID: String = "",
        val snamCheck: String = "",
        val financialCheck: String =""
)