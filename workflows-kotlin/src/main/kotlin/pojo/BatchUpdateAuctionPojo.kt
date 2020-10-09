package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class BatchUpdateAuctionPojo(
        val batchID: String = "",
        val supportField: String = "",
        val auctionStatus: String =""
)