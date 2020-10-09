package com.bioMetanoRevenge.contract

import com.bioMetanoRevenge.state.ExchangeState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class ExchangeContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.bioMetanoRevenge.contract.ExchangeContract"
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
            val exchange = tx.outputsOfType<ExchangeState>().single()
            "All of the participants must be signers." using (signers.containsAll(exchange.participants.map { it.owningKey }))

            // Exchange-specific constraints.
            "GSE and seller cannot be the same entity." using (exchange.GSE != exchange.seller)
            "GSE and buyer cannot be the same entity." using (exchange.GSE != exchange.buyer)
            "seller and buyer cannot be the same entity." using (exchange.seller != exchange.buyer)
            "exchangeType cannot be empty." using (exchange.exchangeType.isNotEmpty())
            "parentBatchID cannot be empty." using (exchange.parentBatchID.isNotEmpty())
            "exchangeCode cannot be empty." using (exchange.exchangeCode.isNotEmpty())
            "month cannot be empty." using (exchange.month.isNotEmpty())
            "remiCode cannot be empty." using (exchange.remiCode.isNotEmpty())
            "initialQuantity must be greater than zero." using (exchange.initialQuantity > 0.0)
            "quantity must be greater than zero." using (exchange.quantity > 0.0)
            "price must be greater than zero." using (exchange.price > 0.0)
            "docRef cannot be empty." using (exchange.docRef.isNotEmpty())
            "exchangeStatus cannot be empty." using (exchange.exchangeStatus.isNotEmpty())
            "exchangeStatus must be \"open\", \"selling\" or \"closed\"." using (exchange.exchangeStatus.equals("open", ignoreCase = true) || exchange.exchangeStatus.equals("selling", ignoreCase = true) || exchange.exchangeStatus.equals("closed", ignoreCase = true))
        }
    }

    private fun verifyUpdate(tx: LedgerTransaction, signers: Set<PublicKey>){
        tx.commands.requireSingleCommand<Commands.Update>()
        requireThat {
            // Generic constraints around the old Exchange transaction.
            "there must be only one exchange input." using (tx.inputs.size == 1)
            val oldExchangeState = tx.inputsOfType<ExchangeState>().single()

            // Generic constraints around the generic update transaction.
            "Only one transaction state should be created." using (tx.outputs.size == 1)
            val newExchangeState = tx.outputsOfType<ExchangeState>().single()
            "All of the participants must be signers." using (signers.containsAll(newExchangeState.participants.map { it.owningKey }))

            // Generic constraints around the new Exchange transaction
            "GSE from old and new Exchange cannot change." using (oldExchangeState.GSE == newExchangeState.GSE)
            "seller from old and new Exchange cannot change." using (oldExchangeState.seller == newExchangeState.seller)
            "buyer from old and new Exchange cannot change." using (oldExchangeState.buyer == newExchangeState.buyer)
            "parentBatchID from old and new Exchange cannot change." using (oldExchangeState.parentBatchID == newExchangeState.parentBatchID)
            "exchangeCode from old and new Exchange cannot change." using (oldExchangeState.exchangeCode == newExchangeState.exchangeCode)
            "month from old and new Exchange cannot change." using (oldExchangeState.month == newExchangeState.month)
            "quantity must be greater or equal than zero." using (newExchangeState.quantity >= 0.0)
            "exchangeStatus cannot be empty." using (newExchangeState.exchangeStatus.isNotEmpty())
            "exchangeStatus must be \"open\", \"selling\" or \"closed\"." using (newExchangeState.exchangeStatus.equals("open", ignoreCase = true) || newExchangeState.exchangeStatus.equals("selling", ignoreCase = true) || newExchangeState.exchangeStatus.equals("closed", ignoreCase = true))
        }
    }

    private fun verifyUpdateAuction(tx: LedgerTransaction, signers: Set<PublicKey>){
        tx.commands.requireSingleCommand<Commands.UpdateAuction>()
        requireThat {
            // Generic constraints around the old Exchange transaction.
            "there must be only one exchange input." using (tx.inputs.size == 1)
            val oldExchangeState = tx.inputsOfType<ExchangeState>().single()

            // Generic constraints around the generic update transaction.
            "Only one transaction state should be created." using (tx.outputs.size == 1)
            val newExchangeState = tx.outputsOfType<ExchangeState>().single()
            "All of the participants must be signers." using (signers.containsAll(newExchangeState.participants.map { it.owningKey }))

            // Generic constraints around the new Exchange transaction
            "GSE from old and new Exchange cannot change." using (oldExchangeState.GSE == newExchangeState.GSE)
            "seller from old and new Exchange cannot change." using (oldExchangeState.seller == newExchangeState.seller)
            "buyer from old and new Exchange cannot change." using (oldExchangeState.buyer == newExchangeState.buyer)
            "parentBatchID from old and new Exchange cannot change." using (oldExchangeState.parentBatchID == newExchangeState.parentBatchID)
            "exchangeCode from old and new Exchange cannot change." using (oldExchangeState.exchangeCode == newExchangeState.exchangeCode)
            "month from old and new Exchange cannot change." using (oldExchangeState.month == newExchangeState.month)
            "quantity must be greater or equal than zero." using (newExchangeState.quantity >= 0.0)
            "supportField cannot be empty." using (newExchangeState.supportField.isNotEmpty())
            "auctionStatus cannot be empty." using (newExchangeState.auctionStatus.isNotEmpty())
        }
    }


    /**
     * This contract only implements three commands: Issue, Update, UpdateAuction.
     */
    interface Commands : CommandData {
        class Issue : Commands, TypeOnlyCommandData()
        class Update: Commands, TypeOnlyCommandData()
        class UpdateAuction: Commands, TypeOnlyCommandData()
    }
}