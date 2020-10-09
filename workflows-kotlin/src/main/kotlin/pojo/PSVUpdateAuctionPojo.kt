package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class PSVUpdateAuctionPojo(
        val transactionCode: String = "",
        val supportField: String = "",
        val auctionStatus: String =""
)