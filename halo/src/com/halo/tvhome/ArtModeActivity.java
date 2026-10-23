package com.halo.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

/** Art Mode on demand: left/right changes the picture, Back returns home. */
public class ArtModeActivity extends Activity {
    private ArtFrameView view;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new ArtFrameView(this);
        setContentView(view);
    }

    @Override
    protected void onStart() {
        super.onStart();
        view.post(view::start);
    }

    @Override
    protected void onStop() {
        view.stop();
        super.onStop();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) view.show(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
}
