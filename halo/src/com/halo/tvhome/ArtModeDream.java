package com.halo.tvhome;

import android.service.dreams.DreamService;

/** Art Mode as the TV's screensaver. */
public class ArtModeDream extends DreamService {
    private ArtFrameView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        view = new ArtFrameView(this);
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        view.post(view::start);
    }

    @Override
    public void onDreamingStopped() {
        view.stop();
        super.onDreamingStopped();
    }
}
