package com.midroid.mi5gtv.prayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        int kind = intent.getIntExtra("kind", -1), prayer = intent.getIntExtra("prayer", 0);
        Settings s = Settings.load(ctx);
        if (kind == Plan.KIND_PRAYER) {
            if (s.modes[prayer] == Settings.MODE_ADHAN && prayer != Engine.SUNRISE) AdhanService.start(ctx, prayer);
            else Banner.show(ctx, ctx.getString(R.string.time_of, Engine.NAMES_AR[prayer]), 20);
        } else if (kind == Plan.KIND_PRE) {
            Banner.show(ctx, ctx.getString(R.string.pre_adhan, Engine.NAMES_AR[prayer], s.preMinutes), 20);
        } else if (kind == Plan.KIND_KAHF) {
            // Title only, by design: no verses or other religious text are shown.
            Banner.show(ctx, ctx.getString(R.string.kahf_title), 30);
        }
        Alarms.arm(ctx);
    }
}
