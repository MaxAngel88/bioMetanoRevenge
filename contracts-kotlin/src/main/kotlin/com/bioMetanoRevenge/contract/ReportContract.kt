package com.bioMetanoRevenge.contract

import com.bioMetanoRevenge.state.ReportState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class ReportContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.bioMetanoRevenge.contract.ReportContract"
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
            val reportState = tx.outputsOfType<ReportState>().single()
            "All of the participants must be signers." using (signers.containsAll(reportState.participants.map { it.owningKey }))

            // ReportState-specific constraints.
            "GSE and Snam cannot be the same entity." using (reportState.GSE != reportState.Snam)
            "GSE and owner cannot be the same entity." using (reportState.GSE != reportState.owner)
            "Snam and owner cannot be the same entity." using (reportState.Snam != reportState.owner)
            "reportID cannot be empty." using (reportState.reportID.isNotEmpty())
            "ownerID cannot be empty." using (reportState.ownerID.isNotEmpty())
            "reportType cannot be empty." using (reportState.reportType.isNotEmpty())
            "remiCode cannot be empty." using (reportState.remiCode.isNotEmpty())
            "remiAddress cannot be empty." using (reportState.remiAddress.isNotEmpty())
            "measuredQuantity must be a number." using (!reportState.measuredQuantity.isNaN())
            "measuredEnergy must be a number." using (!reportState.measuredEnergy.isNaN())
            "measuredPcs must be a number." using (!reportState.measuredPcs.isNaN())
            "measuredPci must be a number." using (!reportState.measuredPci.isNaN())
        }
    }

    private fun verifyUpdate(tx: LedgerTransaction, signers: Set<PublicKey>){
        tx.commands.requireSingleCommand<Commands.Update>()
        requireThat {
            // Generic constraints around the old ReportState transaction.
            "there must be only one ReportState input." using (tx.inputs.size == 1)
            val oldReportState = tx.inputsOfType<ReportState>().single()

            // Generic constraints around the generic update transaction.
            "Only one transaction state should be created." using (tx.outputs.size == 1)
            val newReportState = tx.outputsOfType<ReportState>().single()
            "All of the participants must be signers." using (signers.containsAll(newReportState.participants.map { it.owningKey }))

            // Generic constraints around the new ReportState transaction
            "GSE from old and new ReportState cannot change." using (oldReportState.GSE == newReportState.GSE)
            "Snam from old and new ReportState cannot change." using (oldReportState.Snam == newReportState.GSE)
            "owner from old and new ReportState cannot change." using (oldReportState.owner == newReportState.owner)
            "reportID from old and new ReportState cannot change." using (oldReportState.reportID == newReportState.reportID)
            "ownerID from old and new ReportState cannot change." using (oldReportState.ownerID == newReportState.ownerID)
            "reportType from old and new ReportState cannot change." using (oldReportState.reportType == newReportState.reportType)
            "remiCode from old and new ReportState cannot change." using (oldReportState.remiCode == newReportState.remiCode)
            "remiAddress from old and new ReportState cannot change." using (oldReportState.remiAddress == newReportState.remiAddress)
            "measuredQuantity must be a number." using (!newReportState.measuredQuantity.isNaN())
            "measuredEnergy must be a number." using (!newReportState.measuredEnergy.isNaN())
            "measuredPcs must be a number." using (!newReportState.measuredPcs.isNaN())
            "measuredPci must be a number." using (!newReportState.measuredPci.isNaN())
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