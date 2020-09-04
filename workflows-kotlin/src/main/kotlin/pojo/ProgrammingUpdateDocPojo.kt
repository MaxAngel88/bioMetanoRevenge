package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class ProgrammingUpdateDocPojo(
        val versionFile: String = "",
        val bioAgreementCode: String = "",
        val docRef: String = "",
        val docName: String = ""
)