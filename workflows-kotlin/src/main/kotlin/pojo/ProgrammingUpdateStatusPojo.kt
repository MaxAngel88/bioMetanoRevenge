package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class ProgrammingUpdateStatusPojo(
        val bioAgreementCode: String = "",
        val programmingStatus: String = ""
)