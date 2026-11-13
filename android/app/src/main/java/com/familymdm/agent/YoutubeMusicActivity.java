package com.familymdm.agent;

/**
 * The YouTube Music icon: its own screen (own task) with the same rules and ask-for-access as the YouTube icon, but
 * only music: it opens only music.youtube.com, lists only the songs / albums the parent allowed for Music (never
 * YouTube videos or channels), asks for "a song, album or artist", downloads a song's sound, and takes away
 * "Open app" and the sign-in button.
 */
public class YoutubeMusicActivity extends YoutubeAddonActivity {
    @Override
    protected String home() {
        return "https://music.youtube.com/";
    }

    @Override
    protected boolean music() {
        return true;
    }
}
