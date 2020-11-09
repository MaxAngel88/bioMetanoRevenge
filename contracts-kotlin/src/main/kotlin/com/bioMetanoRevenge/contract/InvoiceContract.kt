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
                is Commands.UpdateStatus -> verifyUpdateStatus(tx, setOfSigners)
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

    private fun verifyUpdateStatus(tx: LedgerTransaction, signers: Set<PublicKey>) {
        tx.commands.requireSingleCommand<Commands.UpdateStatus>()
        requireThat {
            // Generic constraints around the old Agreement transaction.
            "there must be only one invoice input." using (tx.inputs.size == 1)
            val oldInvoiceState = tx.inputsOfType<InvoiceState>().single()

            // Generic constraints around the generic update transaction.
            "Only one transaction state should be created." using (tx.outputs.size == 1)
            val newInvoiceState = tx.outputsOfType<InvoiceState>().single()
            "All of the participants must be signers." using (signers.containsAll(newInvoiceState.participants.map { it.owningKey }))

            // Generic constraints around the new Agreement transaction
            "GSE from old and new Invoice cannot change." using (oldInvoiceState.GSE == newInvoiceState.GSE)
            "ownerParty from old and new Invoice cannot change." using (oldInvoiceState.ownerParty == newInvoiceState.ownerParty)
            "ownerID from old and new Invoice cannot change." using (oldInvoiceState.ownerID == newInvoiceState.ownerID)
            "invoiceID from old and new Invoice cannot change." using (oldInvoiceState.invoiceID == newInvoiceState.invoiceID)
            "invoiceRef from old and new Invoice cannot change." using (oldInvoiceState.invoiceRef == newInvoiceState.invoiceRef)
            "invoiceStatus cannot be empty." using (newInvoiceState.invoiceStatus.isNotEmpty())
        }
    }


    /**
     * This contract only implements two commands: Issue, UpdateStatus.
     */
    interface Commands : CommandData {
        class Issue : Commands, TypeOnlyCommandData()
        class UpdateStatus: Commands, TypeOnlyCommandData()
    }
}