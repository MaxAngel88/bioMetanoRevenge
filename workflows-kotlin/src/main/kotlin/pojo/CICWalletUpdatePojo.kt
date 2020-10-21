package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class CICWalletUpdatePojo(
        val CICWalletID: String = "",
        val CICAddAmount: Double = 0.0
)