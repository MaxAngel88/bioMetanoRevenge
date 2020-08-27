package com.bioMetanoRevenge.contract

import com.bioMetanoRevenge.state.RawMaterialState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class RawMaterialContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.bioMetanoRevenge.contract.RawMaterialContract"
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
            val rawMaterial = tx.outputsOfType<RawMaterialState>().single()
            "All of the participants must be signers." using (signers.containsAll(rawMaterial.participants.map { it.owningKey }))

            // rawMaterial-specific constraints.
            "GSE and produttore cannot be the same entity." using (rawMaterial.GSE != rawMaterial.produttore)
            "rawMaterialChar cannot be empty." using (rawMaterial.rawMaterialChar.isNotEmpty())
            "rawMaterialType cannot be empty." using (rawMaterial.rawMaterialType.isNotEmpty())
            "CERCode cannot be empty." using (rawMaterial.CERCode.isNotEmpty())
            "quantity must be greater than zero." using (rawMaterial.quantity > 0.0)
            "originCountry cannot be empty." using (rawMaterial.originCountry.isNotEmpty())
            "sustainabilityCode cannot be empty." using (rawMaterial.sustainabilityCode.isNotEmpty())
        }
    }

    /**
     * This contract only implements one commands: Issue.
     */
    interface Commands : CommandData {
        class Issue : Commands, TypeOnlyCommandData()
    }
}