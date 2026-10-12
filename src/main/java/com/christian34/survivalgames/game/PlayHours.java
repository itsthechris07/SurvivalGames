package com.christian34.survivalgames.game;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The hours a Varo can be played in (varo.play-hours): "18:00-20:00" or "18-20" for every day, "sat,sun 14:00-22:00"
 * or "mon-fri 18-20" for some days of the week. A time frame may go beyond midnight ("22:00-02:00", the day is the one
 * it starts on). Without time frames the game can always be played.
 *
 * @author Christian34
 */
public final class PlayHours {
    private static final Pattern ENTRY = Pattern.compile(
            "(?:([a-z,\\-]+)\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*-\\s*(\\d{1,2})(?::(\\d{2}))?");
    private static final List<String> DAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");
    private static final int MINUTES_PER_DAY = 24 * 60;
    private final List<Window> windows = new ArrayList<>();
    private final List<String> entries = new ArrayList<>();

    /**
     * @param start   minute of the day the time frame starts at
     * @param minutes its length
     */
    private record Window(Set<DayOfWeek> days, int start, int minutes) {
    }

    /**
     * @param invalid gets the entries that can't be read (they are ignored)
     */
    public PlayHours(List<String> entries, Consumer<String> invalid) {
        for (String raw : entries) {
            String entry = raw.trim().toLowerCase(Locale.ROOT);
            if (entry.isEmpty()) continue;
            Window window = parse(entry);
            if (window == null) {
                invalid.accept(raw);
                continue;
            }
            this.windows.add(window);
            this.entries.add(entry);
        }
    }

    private static Window parse(String entry) {
        Matcher matcher = ENTRY.matcher(entry);
        if (!matcher.matches()) return null;
        Set<DayOfWeek> days = matcher.group(1) == null ? EnumSet.allOf(DayOfWeek.class) : parseDays(matcher.group(1));
        int start = parseTime(matcher.group(2), matcher.group(3));
        int end = parseTime(matcher.group(4), matcher.group(5));
        if (days == null || start < 0 || end < 0 || start >= MINUTES_PER_DAY) return null;
        // 22:00-02:00 ends on the next day
        int minutes = end > start ? end - start : end - start + MINUTES_PER_DAY;
        return new Window(days, start, minutes);
    }

    /**
     * @return the minute of the day (24:00 is the end of the day), -1 if it isn't a time
     */
    private static int parseTime(String hour, String minute) {
        int minutes = Integer.parseInt(hour) * 60 + (minute == null ? 0 : Integer.parseInt(minute));
        boolean valid = minutes <= MINUTES_PER_DAY && (minute == null || Integer.parseInt(minute) < 60);
        return valid ? minutes : -1;
    }

    /**
     * @param text days ("sat,sun") and ranges of days ("mon-fri", "fri-mon")
     */
    private static Set<DayOfWeek> parseDays(String text) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String part : text.split(",")) {
            String[] range = part.split("-", -1);
            int from = day(range[0]);
            int to = day(range[range.length - 1]);
            if (range.length > 2 || from < 0 || to < 0) return null;
            for (int day = from; ; day = (day + 1) % 7) {
                days.add(DayOfWeek.of(day + 1));
                if (day == to) break;
            }
        }
        return days;
    }

    private static int day(String name) {
        return name.length() < 3 ? -1 : DAYS.indexOf(name.substring(0, 3));
    }

    /**
     * @return true if the game can't be played all the time
     */
    public boolean isRestricted() {
        return !windows.isEmpty();
    }

    /**
     * @return the time frames as they are shown to the players
     */
    public List<String> getEntries() {
        return List.copyOf(entries);
    }

    public boolean isOpen(LocalDateTime time) {
        return windows.isEmpty() || end(time) != null;
    }

    /**
     * @return the seconds the game can still be played from this time on (0: not right now)
     */
    public int secondsLeft(LocalDateTime time) {
        if (windows.isEmpty()) return Integer.MAX_VALUE;
        // time frames that follow each other (18-20 and 20-22) count as one
        LocalDateTime limit = time.plusDays(8);
        LocalDateTime closing = time;
        for (LocalDateTime end = end(time); end != null && closing.isBefore(limit); end = end(closing)) {
            closing = end;
        }
        return (int) Math.min(Integer.MAX_VALUE, Duration.between(time, closing).toSeconds());
    }

    /**
     * @return the next time a time frame starts after this time (the time itself if there are no time frames)
     */
    public LocalDateTime nextOpening(LocalDateTime time) {
        LocalDateTime next = null;
        for (Window window : windows) {
            for (int ahead = 0; ahead <= 7; ahead++) {
                LocalDate date = time.toLocalDate().plusDays(ahead);
                LocalDateTime start = date.atStartOfDay().plusMinutes(window.start());
                if (!window.days().contains(date.getDayOfWeek()) || !start.isAfter(time)) continue;
                if (next == null || start.isBefore(next)) next = start;
                break;
            }
        }
        return next == null ? time : next;
    }

    /**
     * @return the end of the time frame the time is in (the latest one), null if it isn't in one
     */
    private LocalDateTime end(LocalDateTime time) {
        LocalDateTime latest = null;
        for (Window window : windows) {
            // the time frame of yesterday may still be going on
            for (int back = 0; back <= 1; back++) {
                LocalDate date = time.toLocalDate().minusDays(back);
                if (!window.days().contains(date.getDayOfWeek())) continue;
                LocalDateTime start = date.atStartOfDay().plusMinutes(window.start());
                LocalDateTime end = start.plusMinutes(window.minutes());
                if (!time.isBefore(start) && time.isBefore(end) && (latest == null || end.isAfter(latest))) latest = end;
            }
        }
        return latest;
    }

}
