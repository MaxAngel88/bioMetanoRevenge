package com.bioMetanoRevenge.contract

import com.bioMetanoRevenge.state.EnrollState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class EnrollContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.bioMetanoRevenge.contract.EnrollContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val commands = tx.commandsOfType<Commands>()
        for(command in commands){
            val setOfSigners = command.signers.toSet()
            when(command.value){
                is Commands.Issue -> verifyIssue(tx, setOfSigners)
                is Commands.Update -> verifyUpdate(tx, setOfSigners)
                else -> throw IllegalArgumentException("Unrecognised command.")
            }
        }
    }

    private fun verifyIssue(tx: LedgerTransaction, signers: Set<PublicKey>) {
        tx.commands.requireSingleCommand<Commands.Issue>()
        requireThat {
            // Generic constraints around the generic create transaction.
            "No inputs should be consumed" using (tx.inputs.isEmpty())
            "Only one output state should be created." using (tx.outputs.size == 1)
            val enroll = tx.outputsOfType<EnrollState>().single()
            "All of the participants must be signers." using (signers.containsAll(enroll.participants.map { it.owningKey }))

            // Enroll-specific constraints.
            "GSE and owner cannot be the same entity." using (enroll.GSE != enroll.owner)
            "enrollType cannot be empty." using (enroll.enrollType.isNotEmpty())
            "subjectFirstName cannot be empty." using (enroll.subjectFirstName.isNotEmpty())
            "subjectLastName cannot be empty." using (enroll.subjectLastName.isNotEmpty())
            "qualificationCode cannot be empty." using (enroll.qualificationCode.isNotEmpty())
            "remiCode cannot be empty." using (enroll.remiCode.isNotEmpty())
            "username cannot be empty." using (enroll.username.isNotEmpty())
            "role cannot be empty." using (enroll.role.isNotEmpty())
            "docRefAutodichiarazione cannot be empty." using (enroll.docRefAutodichiarazione.isNotEmpty())
            "docRefAttestazioniTecniche cannot be empty." using (enroll.docRefAttestazioniTecniche.isNotEmpty())
            "enrollStatus cannot be empty." using (enroll.enrollStatus.isNotEmpty())
            "enrollStatus must be \"Pending\"." using (enroll.enrollStatus.equals("Pending", ignoreCase = true))
            "uuid cannot be empty." using (enroll.uuid.isNotEmpty())
        }
    }

    private fun verifyUpdate(tx: LedgerTransaction, signers: Set<PublicKey>){
        tx.commands.requireSingleCommand<Commands.Update>()
        requireThat {
            // Generic constraints around the old Enroll transaction.
            "there must be only one enroll input." using (tx.inputs.size == 1)
            val oldEnrollState = tx.inputsOfType<EnrollState>().single()

            // Generic constraints around the generic update transaction.
            "Only one transaction state should be created." using (tx.outputs.size == 1)
            val newEnrollState = tx.outputsOfType<EnrollState>().single()
            "All of the participants must be signers." using (signers.containsAll(newEnrollState.participants.map { it.owningKey }))

            // Generic constraints around the new Enroll transaction
            "GSE from old and new Enroll cannot change." using (oldEnrollState.GSE == newEnrollState.GSE)
            "owner from old and new Enroll cannot change." using (oldEnrollState.owner == newEnrollState.owner)
            "enrollStatus must be \"Pending\" or \"Approved\"." using (newEnrollState.enrollStatus.equals("Pending", ignoreCase = true) || newEnrollState.enrollStatus.equals("Approved", ignoreCase = true))
            "uuid cannot be update." using (oldEnrollState.uuid == newEnrollState.uuid)
        }
    }


    /**
     * This contract only implements two commands: Issue, Update.
     */
    interface Commands : CommandData {
        class Issue : Commands, TypeOnlyCommandData()
        class Update: Commands, TypeOnlyCommandData()
    }
}