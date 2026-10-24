package tv.cleanyour.curtain;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Takes the phone's cues: one am broadcast per stage, with the run token, the stage and group as
// enum names and four numbers. Only the shell or the system can send them (the manifest requires
// WRITE_SECURE_SETTINGS of the sender). A malformed cue is dropped; a good one goes to the live
// curtain, never to a new activity.
public final class CueReceiver extends BroadcastReceiver {
    static final String ACTION = "tv.cleanyour.curtain.CUE";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        Cue cue = Cue.parse(
            intent.getStringExtra("token"), intent.getStringExtra("stage"), intent.getStringExtra("group"),
            intent.getIntExtra("count", -1), intent.getIntExtra("total", -1),
            intent.getIntExtra("gcount", -1), intent.getIntExtra("gtotal", -1));
        if (cue != null) CueBus.post(cue);
    }
}
