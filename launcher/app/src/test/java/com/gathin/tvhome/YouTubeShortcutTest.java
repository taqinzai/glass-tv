package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The channel feed's newest video that is not a rerun. */
public class YouTubeShortcutTest {
    private static String entry(String id, String title) {
        return "<entry><id>yt:video:" + id + "</id><yt:videoId>" + id + "</yt:videoId><yt:channelId>UC</yt:channelId><title>" + title + "</title></entry>";
    }

    @Test
    public void skipsReruns() {
        String feed = "<feed><title>My #QuranTime</title>" + entry("AAA", "(Ulangan) Episod 1409")
                + entry("BBB", "Episod 1410 Surah Al-Rum") + "</feed>";
        assertEquals("BBB", YouTubeShortcut.newestVideo(feed));
    }

    @Test
    public void newestWhenAllReruns() {
        assertEquals("AAA", YouTubeShortcut.newestVideo("<feed>" + entry("AAA", "(Ulangan) 1") + entry("BBB", "(Ulangan) 2") + "</feed>"));
        assertNull(YouTubeShortcut.newestVideo("<feed></feed>"));
        assertNull(YouTubeShortcut.newestVideo(null));
    }
}
