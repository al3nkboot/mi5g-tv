package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Mi5G 2.0: exists only so MediaSessionManager lets the launcher read the playing app's session
 * (now playing in the media pill and the Control Center). The phone app grants it with
 * "cmd notification allow_listener"; notifications themselves are never read.
 */
public class MediaListener extends NotificationListenerService {

    /** Mi5G 2.0.1: the system rebinds the listener after a standby force stop; put the key services back. */
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Revival.restore(this);
    }

}
