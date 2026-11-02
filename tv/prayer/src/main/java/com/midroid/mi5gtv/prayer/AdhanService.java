package com.midroid.mi5gtv.prayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;

/**
 * Plays the adhan over any app. Forced adhan (default on, settings forced_adhan): unmutes and raises
 * the media volume to forced_volume %, takes exclusive audio focus so the playing app pauses, and puts
 * the full-screen adhan on top. Volume is restored afterwards.
 */
public class AdhanService extends Service {
    static final String STOP = "com.midroid.mi5gtv.prayer.STOP_ADHAN";
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private int savedVolume = -1;
    private boolean wasMuted;

    static void start(Context ctx, int prayer) {
        Intent i = new Intent(ctx, AdhanService.class).putExtra("prayer", prayer);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
    }

    /** Target media volume index for a percentage. Pure, for tests. */
    static int forcedIndex(int max, int percent) { return Math.max(1, Math.round(max * Settings.clamp(percent, 10, 100) / 100f)); }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        int prayer = intent == null ? 0 : intent.getIntExtra("prayer", 0);
        startAsForeground(prayer);
        Settings s = Settings.load(this);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        if (s.forced) {
            wasMuted = audio.isStreamMute(AudioManager.STREAM_MUSIC);
            savedVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(savedVolume, forcedIndex(max, s.forcedVolume)), 0);
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).setAudioAttributes(attrs).build();
            audio.requestAudioFocus(focus);
            try {
                startActivity(new Intent(this, AdhanActivity.class).putExtra("prayer", prayer)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            } catch (Exception ignored) {
            }
        } else {
            Banner.show(this, getString(R.string.time_of, Engine.NAMES_AR[prayer]), 30);
        }
        if (player != null) player.release();
        player = MediaPlayer.create(this, R.raw.adhan_default, attrs,
                audio.generateAudioSessionId());
        if (player == null) { stopSelf(); return START_NOT_STICKY; }
        player.setOnCompletionListener(p -> stopSelf());
        player.start();
        return START_NOT_STICKY;
    }

    private void startAsForeground(int prayer) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("adhan", getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH));
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, AdhanService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "adhan").setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.time_of, Engine.NAMES_AR[prayer]))
                .addAction(new Notification.Action.Builder(null, getString(R.string.stop), stop).build()).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(7, n);
    }

    @Override
    public void onDestroy() {
        if (player != null) { player.release(); player = null; }
        if (audio != null) {
            if (focus != null) audio.abandonAudioFocusRequest(focus);
            if (savedVolume >= 0) audio.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0);
            if (wasMuted) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
        }
        sendBroadcast(new Intent(AdhanActivity.FINISH).setPackage(getPackageName()));
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
