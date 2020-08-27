package com.bioMetanoRevenge.contract

import com.bioMetanoRevenge.state.ProgrammingState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class ProgrammingContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.bioMetanoRevenge.contract.ProgrammingContract"
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
            val programming = tx.outputsOfType<ProgrammingState>().single()
            "All of the participants must be signers." using (signers.containsAll(programming.participants.map { it.owningKey }))

            // Programming-specific constraints.
            "GSE and produttore cannot be the same entity." using (programming.GSE != programming.produttore)
            "programmingType cannot be empty." using (programming.programmingType.isNotEmpty())
            "programmingType must be equal to RET (rettifica) or PRI (primo invio)" using (programming.programmingType.equals("RET", ignoreCase = true) || programming.programmingType.equals("PRI", ignoreCase = true))
            "bioAgreementCode cannot be empty." using (programming.bioAgreementCode.isNotEmpty())
            "remiCode cannot be empty." using (programming.remiCode.isNotEmpty())
            "docRef cannot be empty." using (programming.docRef.isNotEmpty())
            "docName cannot be empty." using (programming.docName.isNotEmpty())
        }
    }

    private fun verifyUpdate(tx: LedgerTransaction, signers: Set<PublicKey>){
        tx.commands.requireSingleCommand<Commands.Update>()
        requireThat {
            // Generic constraints around the old Programming transaction.
            "there must be only one programming input." using (tx.inputs.size == 1)
            val oldProgrammingState = tx.inputsOfType<ProgrammingState>().single()

            // Generic constraints around the generic update transaction.
            "Only one transaction state should be created." using (tx.outputs.size == 1)
            val newProgrammingState = tx.outputsOfType<ProgrammingState>().single()
            "All of the participants must be signers." using (signers.containsAll(newProgrammingState.participants.map { it.owningKey }))

            // Generic constraints around the new Programming transaction
            "GSE from old and new Programming cannot change." using (oldProgrammingState.GSE == newProgrammingState.GSE)
            "owner from old and new Programming cannot change." using (oldProgrammingState.produttore == newProgrammingState.produttore)
            "bioAgreementCode from old and new Programming cannot change." using (oldProgrammingState.bioAgreementCode == newProgrammingState.bioAgreementCode)
            "versionFile must be update." using (oldProgrammingState.versionFile != newProgrammingState.versionFile)
            "docRef must be update." using (oldProgrammingState.docRef != newProgrammingState.docRef)
            "docName must be update." using (oldProgrammingState.docName != newProgrammingState.docName)
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