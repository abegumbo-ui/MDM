package com.familymdm.agent;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONObject;

import java.io.File;

/** Plays a downloaded video (no internet needed): "id" is the video's id from Downloads. */
public class DownloadPlayerActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        JSONObject o = Downloads.find(this, getIntent().getStringExtra("id"));
        if (o == null) {
            Toast.makeText(this, "That video is no longer downloaded.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        VideoView v = new VideoView(this);
        MediaController mc = new MediaController(this);
        mc.setAnchorView(v);
        v.setMediaController(mc);
        v.setVideoPath(new File(Downloads.dir(this), o.optString("file")).getAbsolutePath());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        root.addView(v, lp);
        setContentView(root);
        setTitle(o.optString("t"));
        v.setOnPreparedListener(p -> v.start());
    }
}
