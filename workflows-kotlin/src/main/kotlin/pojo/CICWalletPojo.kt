package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class CICWalletPojo(
        val CICWalletID: String = "",
        val CICAmount: Double = 0.0
)