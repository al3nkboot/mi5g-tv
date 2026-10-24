package tv.cleanyour.app

import tv.cleanyour.core.store.Clock

// The app's one Clock: wall time for records, elapsedRealtime for timers (it keeps counting while
// the phone sleeps). Named AppClock so it never clashes with adb.session.SystemClock or
// android.os.SystemClock.
object AppClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun monotonicMs(): Long = android.os.SystemClock.elapsedRealtime()
}
