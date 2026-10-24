package tv.cleanyour.core.flows

// Stop and undo ask first (owner, first device test). Which buttons change the TV that way: the
// Working screen's "Stop and undo", a stop card's "Stop here" and "Undo what we did", and the
// same two on Manage's open run card. The screens show the question and send the intent only on
// the answer, so the machines never act before it. Carry on, Try again, Back and the Customise
// snackbar's undo (it only edits the draft) never ask.
enum class Confirm { STOP_AND_UNDO, STOP_HERE, UNDO }

object Confirmations {
    // The Working screen's one button.
    val stopAndUndo: Confirm = Confirm.STOP_AND_UNDO

    // A stop card's button, by the card and its place (SetupMachine.onStopAction does the action).
    fun forStopAction(case: StopCase, action: StopAction): Confirm? = when (case) {
        StopCase.CONNECTION_LOST, StopCase.STEP_FAILED -> when (action) {
            StopAction.SECONDARY -> Confirm.STOP_HERE
            StopAction.TERTIARY -> Confirm.UNDO
            StopAction.PRIMARY -> null
        }
        StopCase.RESUME_AFTER_DEATH -> if (action == StopAction.SECONDARY) Confirm.UNDO else null
        else -> null
    }

    fun forOpenWrite(action: OpenWriteAction): Confirm? = when (action) {
        OpenWriteAction.UNDO -> Confirm.UNDO
        OpenWriteAction.STOP_HERE -> Confirm.STOP_HERE
        OpenWriteAction.CARRY_ON -> null
    }
}
