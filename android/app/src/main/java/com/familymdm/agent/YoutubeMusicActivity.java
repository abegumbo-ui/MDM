package com.familymdm.agent;

/**
 * The YouTube Music icon: the same screen, rules and ask-for-access as the YouTube icon (YoutubeAddonActivity),
 * starting on YouTube Music. It has its own task so the two icons never open each other's screen.
 */
public class YoutubeMusicActivity extends YoutubeAddonActivity {
    @Override
    protected String home() {
        return "https://music.youtube.com/";
    }
}
