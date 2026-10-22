package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** JAKIM e-Solat month timetables, read without org.json. */
public class PrayerTimesTest {

    private static final String MONTH = "{\"prayerTime\":["
            + "{\"hijri\":\"1448-04-19\",\"date\":\"01-Okt-2026\",\"day\":\"Thursday\",\"imsak\":\"05:37:00\","
            + "\"fajr\":\"05:47:00\",\"syuruk\":\"06:55:00\",\"dhuha\":\"07:20:00\",\"dhuhr\":\"12:59:00\","
            + "\"asr\":\"16:12:00\",\"maghrib\":\"19:01:00\",\"isha\":\"20:10:00\"},"
            + "{\"hijri\":\"1448-04-20\",\"date\":\"02-Okt-2026\",\"day\":\"Friday\",\"imsak\":\"05:37:00\","
            + "\"fajr\":\"05:47:00\",\"syuruk\":\"06:55:00\",\"dhuha\":\"07:20:00\",\"dhuhr\":\"12:59:00\","
            + "\"asr\":\"16:12:00\",\"maghrib\":\"19:00:00\",\"isha\":\"20:09:00\"}],"
            + "\"status\":\"OK!\",\"zone\":\"TRG01\"}";

    @Test
    public void readsTheRightDay() {
        assertArrayEquals(new int[]{5 * 60 + 47, 12 * 60 + 59, 16 * 60 + 12, 19 * 60, 20 * 60 + 9},
                PrayerTimes.parseDay(MONTH, 2026, 10, 2));
    }

    @Test
    public void missingDayIsNull() {
        assertNull(PrayerTimes.parseDay(MONTH, 2026, 10, 3));
        assertNull(PrayerTimes.parseDay(MONTH, 2026, 11, 1));
        assertNull(PrayerTimes.parseDay(MONTH, 2025, 10, 1));
        assertNull(PrayerTimes.parseDay(null, 2026, 10, 1));
    }

    @Test
    public void malayMonths() {
        assertEquals(8, PrayerTimes.monthOf("Ogo"));
        assertEquals(3, PrayerTimes.monthOf("Mac"));
        assertEquals(12, PrayerTimes.monthOf("Dis"));
        assertEquals(0, PrayerTimes.monthOf("Xyz"));
    }

    @Test
    public void nextPrayer() {
        int[] t = {347, 779, 972, 1140, 1209};
        assertEquals(0, PrayerTimes.nextIndex(t, 300));
        assertEquals(1, PrayerTimes.nextIndex(t, 347 + 1));
        assertEquals(3, PrayerTimes.nextIndex(t, 1000));
        assertEquals(0, PrayerTimes.nextIndex(t, 1300));   // after Isyak: tomorrow's Subuh
    }
}
