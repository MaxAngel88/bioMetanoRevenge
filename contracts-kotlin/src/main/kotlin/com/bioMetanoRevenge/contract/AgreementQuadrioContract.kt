package com.bioMetanoRevenge.contract

import com.bioMetanoRevenge.state.AgreementQuadrioState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class AgreementQuadrioContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.bioMetanoRevenge.contract.AgreementQuadrioContract"
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
            val agreementQuadrio = tx.outputsOfType<AgreementQuadrioState>().single()
            "All of the participants must be signers." using (signers.containsAll(agreementQuadrio.participants.map { it.owningKey }))

            // Agreement-specific constraints.
            "GSE and ownerParty cannot be the same entity." using (agreementQuadrio.GSE != agreementQuadrio.ownerParty)
            "GSE and counterpartParty cannot be the same entity." using (agreementQuadrio.GSE != agreementQuadrio.counterpartParty)
            "ownerParty and counterpartParty cannot be the same entity." using (agreementQuadrio.ownerParty != agreementQuadrio.counterpartParty)
            "agreementQuadrioID cannot be empty." using (agreementQuadrio.agreementQuadrioID.isNotEmpty())
            "agreementQuadrioCode cannot be empty." using (agreementQuadrio.agreementQuadrioCode.isNotEmpty())
            "agreementQuadrioType cannot be empty." using (agreementQuadrio.agreementQuadrioType.isNotEmpty())
            "owner cannot be empty." using (agreementQuadrio.owner.isNotEmpty())
            "counterpart cannot be empty." using (agreementQuadrio.counterpart.isNotEmpty())
            "owner and counterpart cannot be the same entity." using (agreementQuadrio.owner != agreementQuadrio.counterpart)
            "remiCode cannot be empty." using (agreementQuadrio.remiCode.isNotEmpty())
            "yearRef cannot be empty." using (agreementQuadrio.yearRef.isNotEmpty())
            "yearQuantity must be greater than zero." using (agreementQuadrio.yearQuantity > 0.0)
            "validFrom must be before validTo." using (agreementQuadrio.validFrom.isBefore(agreementQuadrio.validTo))
            "validTo must be after validFrom." using (agreementQuadrio.validTo.isAfter(agreementQuadrio.validFrom))
            "validFrom cannot be equal to validTo." using (agreementQuadrio.validFrom != agreementQuadrio.validTo)
            "agreementQuadrioStatus cannot be empty." using (agreementQuadrio.agreementQuadrioStatus.isNotEmpty())
        }
    }

    private fun verifyUpdateStatus(tx: LedgerTransaction, signers: Set<PublicKey>){
        tx.commands.requireSingleCommand<Commands.UpdateStatus>()
        requireThat {
            // Generic constraints around the old Agreement transaction.
            "there must be only one agreement quadrio input." using (tx.inputs.size == 1)
            val oldAgreementQuadrioState = tx.inputsOfType<AgreementQuadrioState>().single()

            // Generic constraints around the generic update transaction.
            "Only one transaction state should be created." using (tx.outputs.size == 1)
            val newAgreementQuadrioState = tx.outputsOfType<AgreementQuadrioState>().single()
            "All of the participants must be signers." using (signers.containsAll(newAgreementQuadrioState.participants.map { it.owningKey }))

            // Generic constraints around the new Agreement transaction
            "GSE from old and new AgreementQuadrio cannot change." using (oldAgreementQuadrioState.GSE == newAgreementQuadrioState.GSE)
            "ownerParty from old and new AgreementQuadrio cannot change." using (oldAgreementQuadrioState.ownerParty == newAgreementQuadrioState.ownerParty)
            "counterpartParty from old and new AgreementQuadrio cannot change." using (oldAgreementQuadrioState.counterpartParty == newAgreementQuadrioState.counterpartParty)
            "owner from old and new AgreementQuadrio cannot change." using (oldAgreementQuadrioState.owner == newAgreementQuadrioState.owner)
            "counterpart from old and new AgreementQuadrio cannot change." using (oldAgreementQuadrioState.counterpart == newAgreementQuadrioState.counterpart)
            "agreementQuadrioStatus cannot be empty." using (newAgreementQuadrioState.agreementQuadrioStatus.isNotEmpty())
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