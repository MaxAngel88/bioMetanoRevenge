package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class ProgrammingUpdatePojo(
        val versionFile: String = "",
        val bioAgreementCode: String = "",
        val docRef: String = "",
        val docName: String = ""
)