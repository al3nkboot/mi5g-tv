package tv.cleanyour.app

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** OS-scheduled check for the Full build; the controller enforces the 24-hour throttle and dedupes startup. */
class AppUpdateJobService : JobService() {
    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val appGraph = (application as? CytApplication)?.graph ?: return false
        running = jobScope.launch {
            try {
                if (appGraph.store.rules.updateCheck) appGraph.appUpdateController.checkOnLaunch()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // An update check is best-effort. Android may retry the periodic job later.
            } finally {
                if (currentCoroutineContext().isActive) jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        running = null
        return true
    }

    override fun onDestroy() {
        jobScope.cancel()
        super.onDestroy()
    }
}
