package com.asearch.relay;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;

public final class TimeContext {
    public static final ZoneId MALTA = ZoneId.of("Europe/Malta");

    public final long referenceTimestamp;
    public final ZonedDateTime maltaDateTime;
    public final String dayOfWeek;
    public final String dayPart;
    public final boolean weekend;
    public final long millisSincePreviousInteraction;

    private TimeContext(long referenceTimestamp, long previousTimestamp) {
        this.referenceTimestamp = referenceTimestamp;
        this.maltaDateTime = Instant.ofEpochMilli(referenceTimestamp).atZone(MALTA);
        this.dayOfWeek = maltaDateTime.getDayOfWeek().name();
        this.dayPart = dayPart(maltaDateTime.getHour());
        this.weekend = maltaDateTime.getDayOfWeek() == DayOfWeek.SATURDAY
                || maltaDateTime.getDayOfWeek() == DayOfWeek.SUNDAY;
        this.millisSincePreviousInteraction = previousTimestamp > 0
                ? Math.max(0, referenceTimestamp - previousTimestamp)
                : -1;
    }

    public static TimeContext at(long timestamp, long previousTimestamp) {
        return new TimeContext(timestamp, previousTimestamp);
    }

    public static TimeContext now(long previousTimestamp) {
        return at(System.currentTimeMillis(), previousTimestamp);
    }

    public LocalDate resolveRelativeDay(String expression) {
        String value = expression == null ? "" : expression.trim().toLowerCase();
        if (value.contains("tomorrow") || value.contains("għada")) {
            return maltaDateTime.toLocalDate().plusDays(1);
        }
        if (value.contains("today") || value.contains("illum")) {
            return maltaDateTime.toLocalDate();
        }
        return null;
    }

    public boolean isReasonableProfessionalCallTime() {
        int hour = maltaDateTime.getHour();
        return hour >= 9 && hour < 21;
    }

    public String relativeTo(long otherTimestamp) {
        if (otherTimestamp <= 0) return "unknown";
        Duration duration = Duration.between(
                Instant.ofEpochMilli(otherTimestamp),
                Instant.ofEpochMilli(referenceTimestamp)
        ).abs();
        if (duration.toMinutes() < 60) return duration.toMinutes() + " minutes";
        if (duration.toHours() < 48) return duration.toHours() + " hours";
        return duration.toDays() + " days";
    }

    private static String dayPart(int hour) {
        if (hour >= 5 && hour < 12) return "MORNING";
        if (hour >= 12 && hour < 17) return "AFTERNOON";
        if (hour >= 17 && hour < 23) return "EVENING";
        return "LATE_NIGHT";
    }
}

