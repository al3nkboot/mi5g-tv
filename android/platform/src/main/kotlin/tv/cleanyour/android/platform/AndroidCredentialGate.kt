package tv.cleanyour.android.platform

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.store.Clock
import tv.cleanyour.core.store.CredentialGate
import tv.cleanyour.core.store.GateIssuer
import tv.cleanyour.core.store.GateMethod
import tv.cleanyour.core.store.GatePass
import tv.cleanyour.core.store.GateResult

// D17 on Android: one system credential prompt, BIOMETRIC_STRONG or DEVICE_CREDENTIAL, titled
// "Confirm it's you". The platform BiometricPrompt (API 28+) is used directly, so no androidx
// biometric or fragment dependency: API 30+ asks for strong biometrics or the screen lock; API 29
// allows the device credential as fallback; API 28 shows the biometric prompt when one is
// enrolled and otherwise the keyguard's confirm-credential screen. A phone with no screen lock
// gets NotAvailable with a NONE pass (the grey line). This class and FakeCredentialGate are the
// only @GateIssuer users (R0: 6).
class AndroidCredentialGate(
    context: Context,
    private val clock: Clock,
    private val validityMs: Long = 10 * 60_000,
) : CredentialGate {
    private val app = context.applicationContext
    private val keyguard = app.getSystemService(KeyguardManager::class.java)
    private var activityRef: WeakReference<Activity>? = null
    private var pendingConfirm: CompletableDeferred<Boolean>? = null

    // MainActivity registers itself; the prompt needs a window on API 28's keyguard path.
    fun attach(activity: Activity?) { activityRef = activity?.let(::WeakReference) }

    // MainActivity forwards the result of the keyguard confirm-credential screen here.
    fun onConfirmCredentialResult(ok: Boolean) { pendingConfirm?.complete(ok) }

    fun hasScreenLock(): Boolean = keyguard?.isDeviceSecure == true

    @OptIn(GateIssuer::class)
    private fun pass(tvId: TvId, method: GateMethod): GatePass {
        val now = clock.nowMs()
        return GatePass(tvId, method, now, now + validityMs)
    }

    override suspend fun confirm(tvId: TvId, title: String, subtitle: String): GateResult = withContext(Dispatchers.Main) {
        if (!hasScreenLock()) return@withContext GateResult.NotAvailable(pass(tvId, GateMethod.NONE))
        val activity = activityRef?.get()
        val ok = when {
            Build.VERSION.SDK_INT >= 30 -> prompt(activity ?: app, title, subtitle) { setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) }
            Build.VERSION.SDK_INT == 29 -> prompt(activity ?: app, title, subtitle) { @Suppress("DEPRECATION") setDeviceCredentialAllowed(true) }
            else -> keyguardConfirm(activity, title, subtitle)
        }
        when (ok) {
            null -> GateResult.Failed
            else -> GateResult.Passed(pass(tvId, ok))
        }
    }

    // USE_BIOMETRIC is declared by :app's manifest (RFC-W8-7); this library manifest has no permissions.
    @SuppressLint("MissingPermission")
    private suspend fun prompt(ctx: Context, title: String, subtitle: String, configure: BiometricPrompt.Builder.() -> Unit): GateMethod? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            val prompt = BiometricPrompt.Builder(ctx).setTitle(title).setSubtitle(subtitle).apply(configure).build()
            prompt.authenticate(signal, ctx.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val method = if (Build.VERSION.SDK_INT >= 30 && result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL)
                        GateMethod.DEVICE_CREDENTIAL else GateMethod.BIOMETRIC
                    if (cont.isActive) cont.resume(method)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { if (cont.isActive) cont.resume(null) }
            })
            cont.invokeOnCancellation { signal.cancel() }
        }

    // API 28: the keyguard's own credential screen, started for a result by MainActivity.
    @Suppress("DEPRECATION")
    private suspend fun keyguardConfirm(activity: Activity?, title: String, subtitle: String): GateMethod? {
        val a = activity ?: return null
        val intent: Intent = keyguard?.createConfirmDeviceCredentialIntent(title, subtitle) ?: return null
        val done = CompletableDeferred<Boolean>()
        pendingConfirm = done
        a.startActivityForResult(intent, REQUEST_CONFIRM)
        return if (done.await()) GateMethod.DEVICE_CREDENTIAL else null
    }

    companion object {
        const val REQUEST_CONFIRM = 4207
    }
}
