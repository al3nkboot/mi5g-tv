package com.gathin.tvhome;

import android.service.dreams.DreamService;

/** Mi5G 2.0.1: the Arabic city screensaver (see {@link CityScreen}). */
public class CityDream extends DreamService {
    private CityScreen screen;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        screen = new CityScreen(this);
        setContentView(screen.root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        screen.start();
    }

    @Override
    public void onDreamingStopped() {
        screen.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (screen != null) screen.stop();
        super.onDetachedFromWindow();
    }
}
