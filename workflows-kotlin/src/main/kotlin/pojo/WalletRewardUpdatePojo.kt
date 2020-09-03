package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class WalletRewardUpdatePojo(
        val walletID: String = "",
        val rewardPoint: Int = 0,
        val reason: String = ""
)