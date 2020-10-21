package com.bioMetanoRevenge.contract

import com.bioMetanoRevenge.state.CICWalletState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class CICWalletContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.bioMetanoRevenge.contract.CICWalletContract"
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
            val cicWalletState = tx.outputsOfType<CICWalletState>().single()
            "All of the participants must be signers." using (signers.containsAll(cicWalletState.participants.map { it.owningKey }))

            // CICWallet-specific constraints.
            "GSE and owner cannot be the same entity." using (cicWalletState.GSE != cicWalletState.owner)
            "CICWalletID cannot be empty." using (cicWalletState.CICWalletID.isNotEmpty())
        }
    }

    private fun verifyUpdate(tx: LedgerTransaction, signers: Set<PublicKey>){
        tx.commands.requireSingleCommand<Commands.Update>()
        requireThat {
            // Generic constraints around the old CICWallet transaction.
            "there must be only one CICWallet input." using (tx.inputs.size == 1)
            val oldCICWalletState = tx.inputsOfType<CICWalletState>().single()

            // Generic constraints around the generic update transaction.
            "Only one transaction state should be created." using (tx.outputs.size == 1)
            val newCICWalletState = tx.outputsOfType<CICWalletState>().single()
            "All of the participants must be signers." using (signers.containsAll(newCICWalletState.participants.map { it.owningKey }))

            // Generic constraints around the new CICWallet transaction
            "GSE from old and new CICWallet cannot change." using (oldCICWalletState.GSE == newCICWalletState.GSE)
            "owner from old and new CICWallet cannot change." using (oldCICWalletState.owner == newCICWalletState.owner)
            "CICWalletID from old and new CICWallet cannot change." using (oldCICWalletState.CICWalletID == newCICWalletState.CICWalletID)
            "CICAmount from old and new CICWallet must change." using (oldCICWalletState.CICAmount != newCICWalletState.CICAmount)
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