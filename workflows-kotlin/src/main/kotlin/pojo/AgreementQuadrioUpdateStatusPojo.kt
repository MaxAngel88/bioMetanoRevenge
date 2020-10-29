package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class AgreementQuadrioUpdateStatusPojo(
        val agreementQuadrioID: String = "",
        val agreementQuadrioStatus: String = ""
)