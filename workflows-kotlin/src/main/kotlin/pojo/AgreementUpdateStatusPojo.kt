package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class AgreementUpdateStatusPojo(
        val agreementID: String = "",
        val agreementStatus: String = ""
)