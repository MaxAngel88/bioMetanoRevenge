package pojo

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class ProgrammingPojo(
        val sendDate: String = "",
        val monthYear: String = "",
        val programmingType: String = "",
        val versionFile: String = "",
        val bioAgreementCode: String = "",
        val remiCode: String = "",
        val docRef: String = "",
        val docName: String = ""
)