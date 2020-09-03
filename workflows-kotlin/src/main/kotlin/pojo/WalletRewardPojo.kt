package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class WalletRewardPojo(
        val walletID: String = "",
        val rewardPoint: Int = 0,
        val reason: String = ""
)