package com.bioMetanoRevenge.contract

import com.bioMetanoRevenge.state.AgreementState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class AgreementContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.bioMetanoRevenge.contract.AgreementContract"
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
            val agreement = tx.outputsOfType<AgreementState>().single()
            "All of the participants must be signers." using (signers.containsAll(agreement.participants.map { it.owningKey }))

            // Agreement-specific constraints.
            "GSE and ownerParty cannot be the same entity." using (agreement.GSE != agreement.ownerParty)
            "GSE and counterpartParty cannot be the same entity." using (agreement.GSE != agreement.counterpartParty)
            "ownerParty and counterpartParty cannot be the same entity." using (agreement.ownerParty != agreement.counterpartParty)
            "agreementID cannot be empty." using (agreement.agreementID.isNotEmpty())
            "agreementCode cannot be empty." using (agreement.agreementCode.isNotEmpty())
            "agreementType cannot be empty." using (agreement.agreementType.isNotEmpty())
            "agreementSubType cannot be empty." using (agreement.agreementSubType.isNotEmpty())
            "owner cannot be empty." using (agreement.owner.isNotEmpty())
            "counterpart cannot be empty." using (agreement.counterpart.isNotEmpty())
            "owner and counterpart cannot be the same entity." using (agreement.owner != agreement.counterpart)
            "energy must be greater than zero." using (agreement.energy > 0.0)
            "dcq must be greater than zero." using (agreement.dcq > 0.0)
            "price must be greater than zero." using (agreement.price > 0.0)
            "validFrom cannot be empty." using (agreement.validFrom.toString().isBlank())
            "validTo cannot be empty." using (agreement.validTo.toString().isBlank())
            "validFrom must be before validTo." using (agreement.validFrom.isBefore(agreement.validTo))
        }
    }

    private fun verifyUpdate(tx: LedgerTransaction, signers: Set<PublicKey>){
        tx.commands.requireSingleCommand<Commands.Update>()
        requireThat {
            // Generic constraints around the old Agreement transaction.
            "there must be only one agreement input." using (tx.inputs.size == 1)
            val oldAgreementState = tx.inputsOfType<AgreementState>().single()

            // Generic constraints around the generic update transaction.
            "Only one transaction state should be created." using (tx.outputs.size == 1)
            val newAgreementState = tx.outputsOfType<AgreementState>().single()
            "All of the participants must be signers." using (signers.containsAll(newAgreementState.participants.map { it.owningKey }))

            // Generic constraints around the new Agreement transaction
            "GSE from old and new Agreement cannot change." using (oldAgreementState.GSE == newAgreementState.GSE)
            "ownerParty from old and new Agreement cannot change." using (oldAgreementState.ownerParty == newAgreementState.ownerParty)
            "counterpartParty from old and new Agreement cannot change." using (oldAgreementState.counterpartParty == newAgreementState.counterpartParty)
            "owner from old and new Agreement cannot change." using (oldAgreementState.owner == newAgreementState.owner)
            "counterpart from old and new Agreement cannot change." using (oldAgreementState.counterpart == newAgreementState.counterpart)
            "agreementID from old and new Agreement cannot change." using (oldAgreementState.agreementID == newAgreementState.agreementID)
            "agreementCode from old and new Agreement cannot change." using (oldAgreementState.agreementCode == newAgreementState.agreementCode)
            "energy must be greater than zero." using (newAgreementState.energy > 0.0)
            "dcq must be greater than zero." using (newAgreementState.dcq > 0.0)
            "price must be greater than zero." using (newAgreementState.price > 0.0)
            "validFrom cannot be empty." using (newAgreementState.validFrom.toString().isBlank())
            "validTo cannot be empty." using (newAgreementState.validTo.toString().isBlank())
            "validFrom must be before validTo." using (newAgreementState.validFrom.isBefore(newAgreementState.validTo))
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