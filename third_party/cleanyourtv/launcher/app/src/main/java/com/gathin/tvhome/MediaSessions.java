package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import java.util.List;

/** Mi5G 2.0: the active media session, or null when nothing plays or access is not granted. */
final class MediaSessions {
    private MediaSessions() {}

    static final class Now {
        final String title, app; final boolean playing; final MediaController controller;
        Now(String title, String app, boolean playing, MediaController c) { this.title = title; this.app = app; this.playing = playing; this.controller = c; }
    }

    /** Null = no access (listener not allowed); empty title = nothing playing. */
    static Now current(Context ctx) {
        MediaSessionManager m = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (m == null) return null;
        List<MediaController> list;
        try {
            list = m.getActiveSessions(new ComponentName(ctx, MediaListener.class));
        } catch (SecurityException e) {
            return null;
        }
        for (MediaController c : list) {
            PlaybackState st = c.getPlaybackState();
            MediaMetadata md = c.getMetadata();
            String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (title == null || title.isEmpty()) continue;
            boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
            return new Now(title, appLabel(ctx, c.getPackageName()), playing, c);
        }
        return new Now("", "", false, null);
    }

    static void toggle(Now n) {
        if (n == null || n.controller == null) return;
        if (n.playing) n.controller.getTransportControls().pause(); else n.controller.getTransportControls().play();
    }

    private static String appLabel(Context ctx, String pkg) {
        try {
            return String.valueOf(ctx.getPackageManager().getApplicationLabel(ctx.getPackageManager().getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }
}
