package tv.cleanyour.core.flows

import kotlin.test.Test
import kotlin.test.assertEquals

// Every stop card button that stops a run or undoes TV changes asks first, and no other one does.
class ConfirmationsTest {
    // The labels that stop or undo: "Stop here" and "Undo what we did" (stop cards), by their keys.
    private val asks = mapOf(
        CopyKey.STOP_CONNECTION_LOST_SECONDARY to Confirm.STOP_HERE,
        CopyKey.STOP_CONNECTION_LOST_TERTIARY to Confirm.UNDO,
        CopyKey.STOP_RESUME_SECONDARY to Confirm.UNDO,
    )

    @Test fun everyStopAndUndoButtonOnAStopCardAsksFirst() {
        for (case in StopCase.entries) {
            val keys = stopKeys(case)
            val labels = mapOf(StopAction.PRIMARY to keys.primary, StopAction.SECONDARY to keys.secondary, StopAction.TERTIARY to keys.tertiary)
            for ((action, key) in labels) {
                val expected = key?.let { asks[it] }
                assertEquals(expected, Confirmations.forStopAction(case, action), "$case $action ($key)")
            }
        }
    }

    @Test fun manageOpenRunCardAsksBeforeUndoAndStop() {
        assertEquals(Confirm.UNDO, Confirmations.forOpenWrite(OpenWriteAction.UNDO))
        assertEquals(Confirm.STOP_HERE, Confirmations.forOpenWrite(OpenWriteAction.STOP_HERE))
        assertEquals(null, Confirmations.forOpenWrite(OpenWriteAction.CARRY_ON))
        assertEquals(Confirm.STOP_AND_UNDO, Confirmations.stopAndUndo)
    }
}
