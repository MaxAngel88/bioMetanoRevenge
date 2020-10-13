package com.bioMetanoRevenge.contract

import com.bioMetanoRevenge.state.PSVState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class PSVContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.bioMetanoRevenge.contract.PSVContract"
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
                is Commands.UpdateAuction -> verifyUpdateAuction(tx, setOfSigners)
                is Commands.UpdateCheck -> verifyUpdateCheck(tx, setOfSigners)
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
            val psvState = tx.outputsOfType<PSVState>().single()
            "All of the participants must be signers." using (signers.containsAll(psvState.participants.map { it.owningKey }))

            // PSV-specific constraints.
            "GSE and seller cannot be the same entity." using (psvState.GSE != psvState.seller)
            "GSE and buyer cannot be the same entity." using (psvState.GSE != psvState.buyer)
            "GSE and Snam cannot be the same entity." using (psvState.GSE != psvState.Snam)
            "seller and buyer cannot be the same entity." using (psvState.seller != psvState.buyer)
            "transactionType cannot be empty." using (psvState.transactionType.isNotEmpty())
            "parentBatchID cannot be empty." using (psvState.parentBatchID.isNotEmpty())
            "transactionCode cannot be empty." using (psvState.transactionCode.isNotEmpty())
            "month cannot be empty." using (psvState.month.isNotEmpty())
            "remiCode cannot be empty." using (psvState.remiCode.isNotEmpty())
            "initialQuantity must be greater than zero." using (psvState.initialQuantity > 0.0)
            "quantity must be greater than zero." using (psvState.quantity > 0.0)
            "price must be greater than zero." using (psvState.price > 0.0)
            "pcs must be greater than zero." using (psvState.pcs > 0.0)
            "pci must be greater than zero." using (psvState.pci > 0.0)
            "docRef cannot be empty." using (psvState.docRef.isNotEmpty())
            "transactionStatus cannot be empty." using (psvState.transactionStatus.isNotEmpty())
            "transactionStatus must be \"open\", \"selling\" or \"closed\"." using (psvState.transactionStatus.equals("open", ignoreCase = true) || psvState.transactionStatus.equals("selling", ignoreCase = true) || psvState.transactionStatus.equals("closed", ignoreCase = true))
        }
    }

    private fun verifyUpdate(tx: LedgerTransaction, signers: Set<PublicKey>){
        tx.commands.requireSingleCommand<Commands.Update>()
        requireThat {
            // Generic constraints around the old Exchange transaction.
            "there must be only one exchange input." using (tx.inputs.size == 1)
            val oldPSVState = tx.inputsOfType<PSVState>().single()

            // Generic constraints around the generic update transaction.
            "Only one transaction state should be created." using (tx.outputs.size == 1)
            val newPSVState = tx.outputsOfType<PSVState>().single()
            "All of the participants must be signers." using (signers.containsAll(newPSVState.participants.map { it.owningKey }))

            // Generic constraints around the new PSVState transaction
            "GSE from old and new PSVState cannot change." using (oldPSVState.GSE == newPSVState.GSE)
            "seller from old and new PSVState cannot change." using (oldPSVState.seller == newPSVState.seller)
            "buyer from old and new PSVState cannot change." using (oldPSVState.buyer == newPSVState.buyer)
            "Snam from old and new PSVState cannot change." using (oldPSVState.Snam == newPSVState.Snam)
            "parentBatchID from old and new PSVState cannot change." using (oldPSVState.parentBatchID == newPSVState.parentBatchID)
            "transactionCode from old and new PSVState cannot change." using (oldPSVState.transactionCode == newPSVState.transactionCode)
            "month from old and new PSVState cannot change." using (oldPSVState.month == newPSVState.month)
            "quantity must be greater or equal than zero." using (newPSVState.quantity >= 0.0)
            "transactionStatus cannot be empty." using (newPSVState.transactionStatus.isNotEmpty())
            "transactionStatus must be \"open\", \"selling\" or \"closed\"." using (newPSVState.transactionStatus.equals("open", ignoreCase = true) || newPSVState.transactionStatus.equals("selling", ignoreCase = true) || newPSVState.transactionStatus.equals("closed", ignoreCase = true))
        }
    }

    private fun verifyUpdateAuction(tx: LedgerTransaction, signers: Set<PublicKey>){
        tx.commands.requireSingleCommand<Commands.UpdateAuction>()
        requireThat {
            // Generic constraints around the old Exchange transaction.
            "there must be only one exchange input." using (tx.inputs.size == 1)
            val oldPSVState = tx.inputsOfType<PSVState>().single()

            // Generic constraints around the generic update transaction.
            "Only one transaction state should be created." using (tx.outputs.size == 1)
            val newPSVState = tx.outputsOfType<PSVState>().single()
            "All of the participants must be signers." using (signers.containsAll(newPSVState.participants.map { it.owningKey }))

            // Generic constraints around the new PSVState transaction
            "GSE from old and new PSVState cannot change." using (oldPSVState.GSE == newPSVState.GSE)
            "seller from old and new PSVState cannot change." using (oldPSVState.seller == newPSVState.seller)
            "buyer from old and new PSVState cannot change." using (oldPSVState.buyer == newPSVState.buyer)
            "Snam from old and new PSVState cannot change." using (oldPSVState.Snam == newPSVState.Snam)
            "parentBatchID from old and new PSVState cannot change." using (oldPSVState.parentBatchID == newPSVState.parentBatchID)
            "transactionCode from old and new PSVState cannot change." using (oldPSVState.transactionCode == newPSVState.transactionCode)
            "month from old and new PSVState cannot change." using (oldPSVState.month == newPSVState.month)
            "quantity must be greater or equal than zero." using (newPSVState.quantity >= 0.0)
            "supportField cannot be empty." using (newPSVState.supportField.isNotEmpty())
            "auctionStatus cannot be empty." using (newPSVState.auctionStatus.isNotEmpty())
        }
    }

    private fun verifyUpdateCheck(tx: LedgerTransaction, signers: Set<PublicKey>){
        tx.commands.requireSingleCommand<Commands.UpdateCheck>()
        requireThat {
            // Generic constraints around the old Exchange transaction.
            "there must be only one exchange input." using (tx.inputs.size == 1)
            val oldPSVState = tx.inputsOfType<PSVState>().single()

            // Generic constraints around the generic update transaction.
            "Only one transaction state should be created." using (tx.outputs.size == 1)
            val newPSVState = tx.outputsOfType<PSVState>().single()
            "All of the participants must be signers." using (signers.containsAll(newPSVState.participants.map { it.owningKey }))

            // Generic constraints around the new PSVState transaction
            "GSE from old and new PSVState cannot change." using (oldPSVState.GSE == newPSVState.GSE)
            "seller from old and new PSVState cannot change." using (oldPSVState.seller == newPSVState.seller)
            "buyer from old and new PSVState cannot change." using (oldPSVState.buyer == newPSVState.buyer)
            "Snam from old and new PSVState cannot change." using (oldPSVState.Snam == newPSVState.Snam)
            "parentBatchID from old and new PSVState cannot change." using (oldPSVState.parentBatchID == newPSVState.parentBatchID)
            "transactionCode from old and new PSVState cannot change." using (oldPSVState.transactionCode == newPSVState.transactionCode)
            "month from old and new PSVState cannot change." using (oldPSVState.month == newPSVState.month)
            "quantity must be greater or equal than zero." using (newPSVState.quantity >= 0.0)
            "snamCheck cannot be empty." using (newPSVState.snamCheck.isNotEmpty())
            "financialCheck cannot be empty." using (newPSVState.financialCheck.isNotEmpty())
        }
    }


    /**
     * This contract only implements four commands: Issue, Update, UpdateAuction, UpdateCheck.
     */
    interface Commands : CommandData {
        class Issue : Commands, TypeOnlyCommandData()
        class Update: Commands, TypeOnlyCommandData()
        class UpdateAuction: Commands, TypeOnlyCommandData()
        class UpdateCheck: Commands, TypeOnlyCommandData()
    }
}