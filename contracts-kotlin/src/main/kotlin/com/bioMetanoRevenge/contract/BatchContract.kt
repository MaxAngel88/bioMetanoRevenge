package com.bioMetanoRevenge.contract

import com.bioMetanoRevenge.state.BatchState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class BatchContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.bioMetanoRevenge.contract.BatchContract"
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
            val batch = tx.outputsOfType<BatchState>().single()
            "All of the participants must be signers." using (signers.containsAll(batch.participants.map { it.owningKey }))

            // Batch-specific constraints.
            "GSE and produttore cannot be the same entity." using (batch.GSE != batch.produttore)
            "GSE and Snam cannot be the same entity." using (batch.GSE != batch.Snam)
            "GSE and counterpart cannot be the same entity." using (batch.GSE != batch.counterpart)
            "produttore and counterpart cannot be the same entity." using (batch.produttore != batch.counterpart)
            "idProducer cannot be empty." using (batch.idProducer.isNotEmpty())
            "idCounterpart cannot be empty." using (batch.idCounterpart.isNotEmpty())
            "idProducer and idCounterpart cannot be the same entity." using (batch.idProducer != batch.idCounterpart)
            "transacionType cannot be empty." using (batch.transactionType.isNotEmpty())
            "batchID cannot be empty." using (batch.batchID.isNotEmpty())
            "month cannot be empty." using (batch.month.isNotEmpty())
            "remiCode cannot be empty." using (batch.remiCode.isNotEmpty())
            "initialQuantity must be greater than zero." using (batch.initialQuantity > 0.0)
            "quantity must be greater than zero." using (batch.quantity > 0.0)
            "energy must be greater than zero." using (batch.energy > 0.0)
            "price must be greater than zero." using (batch.price > 0.0)
            "energy must be greater than zero." using (batch.energy > 0.0)
            "averageProductionCapacity must be greater than zero." using (batch.averageProductionCapacity > 0.0)
            "maxProductionCapacity must be greater than zero." using (batch.maxProductionCapacity > 0.0)
            "annualEstimate must be greater than zero." using (batch.annualEstimate > 0.0)
            "pcs must be greater than zero." using (batch.pcs > 0.0)
            "pci must be greater than zero." using (batch.pci > 0.0)
            "docRef cannot be empty." using (batch.docRef.isNotEmpty())
            "batchStatus cannot be empty." using (batch.batchStatus.isNotEmpty())
            "batchStatus must be \"open\", \"selling\" or \"closed\"." using (batch.batchStatus.equals("open", ignoreCase = true) || batch.batchStatus.equals("selling", ignoreCase = true) || batch.batchStatus.equals("closed", ignoreCase = true))
        }
    }

    private fun verifyUpdate(tx: LedgerTransaction, signers: Set<PublicKey>){
        tx.commands.requireSingleCommand<Commands.Update>()
        requireThat {
            // Generic constraints around the old Batch transaction.
            "there must be only one batch input." using (tx.inputs.size == 1)
            val oldBatchState = tx.inputsOfType<BatchState>().single()

            // Generic constraints around the generic update transaction.
            "Only one transaction state should be created." using (tx.outputs.size == 1)
            val newBatchState = tx.outputsOfType<BatchState>().single()
            "All of the participants must be signers." using (signers.containsAll(newBatchState.participants.map { it.owningKey }))

            // Generic constraints around the new Batch transaction
            "GSE from old and new Batch cannot change." using (oldBatchState.GSE == newBatchState.GSE)
            "Snam from old and new Batch cannot change." using (oldBatchState.Snam == newBatchState.Snam)
            "produttore from old and new Batch cannot change." using (oldBatchState.produttore == newBatchState.produttore)
            "counterpart from old and new Batch cannot change." using (oldBatchState.counterpart == newBatchState.counterpart)
            "idProducer from old and new Batch cannot change." using (oldBatchState.idProducer == newBatchState.idProducer)
            "idCounterpart from old and new Batch cannot change." using (oldBatchState.idCounterpart == newBatchState.idCounterpart)
            "quantity must be greater or equal than zero." using (newBatchState.quantity >= 0.0)
            "batchStatus cannot be empty." using (newBatchState.batchStatus.isNotEmpty())
            "batchStatus must be \"open\", \"selling\" or \"closed\"." using (newBatchState.batchStatus.equals("open", ignoreCase = true) || newBatchState.batchStatus.equals("selling", ignoreCase = true) || newBatchState.batchStatus.equals("closed", ignoreCase = true))
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