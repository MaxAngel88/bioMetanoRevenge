package com.bioMetanoRevenge.contract

import com.bioMetanoRevenge.state.InvoiceState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class InvoiceContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.bioMetanoRevenge.contract.InvoiceContract"
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
            val invoiceState = tx.outputsOfType<InvoiceState>().single()
            "All of the participants must be signers." using (signers.containsAll(invoiceState.participants.map { it.owningKey }))

            // Invoice-specific constraints.
            "GSE and owner cannot be the same entity." using (invoiceState.GSE != invoiceState.ownerParty)
            "ownerID cannot be empty." using (invoiceState.ownerID.isNotEmpty())
            "invoiceID cannot be empty." using (invoiceState.invoiceID.isNotEmpty())
            "invoiceRef cannot be empty." using (invoiceState.invoiceRef.isNotEmpty())
            "parentQuadrioID cannot be empty." using (invoiceState.parentQuadrioID.isNotEmpty())
            "unityPrice must be greater than zero." using (invoiceState.unityPrice > 0.0)
            "quantity must be greater than zero." using (invoiceState.quantity > 0.0)
            "productType cannot be empty." using (invoiceState.productType.isNotEmpty())
        }
    }


    /**
     * This contract only implements one command: Issue.
     */
    interface Commands : CommandData {
        class Issue : Commands, TypeOnlyCommandData()
    }
}