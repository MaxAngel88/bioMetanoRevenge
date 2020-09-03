package com.bioMetanoRevenge.contract

import com.bioMetanoRevenge.state.WalletRewardState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class WalletRewardContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.bioMetanoRevenge.contract.WalletRewardContract"
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
            val walletRewardState = tx.outputsOfType<WalletRewardState>().single()
            "All of the participants must be signers." using (signers.containsAll(walletRewardState.participants.map { it.owningKey }))

            // WalletReward-specific constraints.
            "GSE and owner cannot be the same entity." using (walletRewardState.GSE != walletRewardState.owner)
            "walletID cannot be empty." using (walletRewardState.walletID.isNotEmpty())
            "reason cannot be empty." using (walletRewardState.reason.isNotEmpty())
        }
    }

    private fun verifyUpdate(tx: LedgerTransaction, signers: Set<PublicKey>){
        tx.commands.requireSingleCommand<Commands.Update>()
        requireThat {
            // Generic constraints around the old WalletReward transaction.
            "there must be only one walletReward input." using (tx.inputs.size == 1)
            val oldWalletRewardState = tx.inputsOfType<WalletRewardState>().single()

            // Generic constraints around the generic update transaction.
            "Only one transaction state should be created." using (tx.outputs.size == 1)
            val newWalletRewardState = tx.outputsOfType<WalletRewardState>().single()
            "All of the participants must be signers." using (signers.containsAll(newWalletRewardState.participants.map { it.owningKey }))

            // Generic constraints around the new WalletReward transaction
            "GSE from old and new WalletReward cannot change." using (oldWalletRewardState.GSE == newWalletRewardState.GSE)
            "owner from old and new WalletReward cannot change." using (oldWalletRewardState.owner == newWalletRewardState.owner)
            "walletID from old and new WalletReward cannot change." using (oldWalletRewardState.walletID == newWalletRewardState.walletID)
            "rewardPoint from old and new WalletReward must change." using (oldWalletRewardState.rewardPoint != newWalletRewardState.rewardPoint)
            "reason from old and new WalletReward must change." using (oldWalletRewardState.reason != newWalletRewardState.reason)
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