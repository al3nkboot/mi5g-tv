package tv.cleanyour.core.flows

// What the wizard screens can ask for: the intents (with RFC-W8-1's StopAction and RejectIdentity),
// system back, which must answer at once, and closing an inline card. The SetupMachine implements
// it; screenshot tests pass NoSetupActions.
interface SetupActions {
    fun intent(intent: SetupIntent)
    fun back(): Boolean
    fun dismissCard()
}

object NoSetupActions : SetupActions {
    override fun intent(intent: SetupIntent) {}
    override fun back(): Boolean = false
    override fun dismissCard() {}
}
