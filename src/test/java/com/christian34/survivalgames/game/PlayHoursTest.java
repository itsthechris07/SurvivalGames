package com.christian34.survivalgames.game;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The time frames of varo.play-hours.
 *
 * @author Christian34
 */
class PlayHoursTest {
    private final List<String> invalid = new ArrayList<>();

    private PlayHours hours(String... entries) {
        return new PlayHours(List.of(entries), invalid::add);
    }

    /**
     * 2026-10-12 is a monday
     */
    private static LocalDateTime monday(int hour, int minute) {
        return LocalDateTime.of(2026, 10, 12, hour, minute);
    }

    @Test
    void alwaysOpenWithoutTimeFrames() {
        PlayHours hours = hours();
        assertFalse(hours.isRestricted());
        assertTrue(hours.isOpen(monday(3, 0)));
        assertEquals(Integer.MAX_VALUE, hours.secondsLeft(monday(3, 0)));
        assertEquals(monday(3, 0), hours.nextOpening(monday(3, 0)));
    }

    @Test
    void everyDay() {
        PlayHours hours = hours("18:00-20:00");
        assertTrue(hours.isRestricted());
        assertFalse(hours.isOpen(monday(17, 59)));
        assertTrue(hours.isOpen(monday(18, 0)));
        assertTrue(hours.isOpen(monday(19, 59)));
        assertFalse(hours.isOpen(monday(20, 0)));
        assertEquals(30 * 60, hours.secondsLeft(monday(19, 30)));
        assertEquals(0, hours.secondsLeft(monday(20, 0)));
        assertEquals(monday(18, 0), hours.nextOpening(monday(12, 0)));
        assertEquals(monday(18, 0).plusDays(1), hours.nextOpening(monday(18, 0)));
        assertEquals(List.of("18:00-20:00"), hours.getEntries());
    }

    @Test
    void shortFormAndSeveralTimeFrames() {
        PlayHours hours = hours("8-10", " 18 - 20 ", "20:00-21:30");
        assertTrue(hours.isOpen(monday(9, 0)));
        assertFalse(hours.isOpen(monday(12, 0)));
        assertEquals(monday(18, 0), hours.nextOpening(monday(12, 0)));
        assertEquals(150 * 60, hours.secondsLeft(monday(19, 0)), "time frames that follow each other");
        assertTrue(invalid.isEmpty(), invalid.toString());
    }

    @Test
    void daysOfTheWeek() {
        PlayHours hours = hours("mon-fri 18:00-20:00", "Sat,Sunday 14:00-22:00");
        assertTrue(hours.isOpen(monday(19, 0)));
        assertFalse(hours.isOpen(monday(15, 0)));
        LocalDateTime saturday = monday(15, 0).plusDays(5);
        assertTrue(hours.isOpen(saturday));
        assertFalse(hours.isOpen(saturday.withHour(23)));
        assertEquals(monday(18, 0).plusDays(7), hours.nextOpening(saturday.plusDays(1).withHour(22)));
        assertTrue(invalid.isEmpty(), invalid.toString());

        PlayHours weekend = hours("fri-mon 10-12");
        assertTrue(weekend.isOpen(monday(11, 0)));
        assertFalse(weekend.isOpen(monday(11, 0).plusDays(1)));
        assertEquals(monday(10, 0).plusDays(4), weekend.nextOpening(monday(12, 0)));
    }

    @Test
    void beyondMidnight() {
        PlayHours hours = hours("fri 22:00-02:00");
        LocalDateTime friday = monday(23, 0).plusDays(4);
        assertTrue(hours.isOpen(friday));
        assertTrue(hours.isOpen(friday.plusHours(2)), "saturday 01:00");
        assertFalse(hours.isOpen(friday.plusHours(3)));
        assertFalse(hours.isOpen(monday(1, 0).plusDays(4)), "friday 01:00");
        assertEquals(3 * 3600, hours.secondsLeft(friday));

        PlayHours allDay = hours("0-24");
        assertTrue(allDay.isOpen(monday(0, 0)));
        assertTrue(allDay.secondsLeft(monday(12, 0)) > 7 * 24 * 3600);
    }

    @Test
    void invalidEntriesAreIgnored() {
        PlayHours hours = hours("18:00-20:00", "25-26", "18:75-20", "someday 18-20", "evening", "");
        assertEquals(List.of("25-26", "18:75-20", "someday 18-20", "evening"), invalid);
        assertEquals(List.of("18:00-20:00"), hours.getEntries());
        assertFalse(hours("nonsense").isRestricted(), "nothing valid: always open");
    }

}
