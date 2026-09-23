package com.aegispunish.core.util;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeParser {

    private static final Pattern PATTERN = Pattern.compile("(\\d+)([smhdMa])");

    public static Duration parse(String input) {
        if (input == null || input.isBlank() || input.equalsIgnoreCase("permanent") || input.equalsIgnoreCase("permanente")) {
            return null;
        }

        Matcher matcher = PATTERN.matcher(input);
        long totalSeconds = 0;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);

            switch (unit) {
                case "s" -> totalSeconds += amount;
                case "m" -> totalSeconds += amount * 60;
                case "h" -> totalSeconds += amount * 3600;
                case "d" -> totalSeconds += amount * 86400;
                case "M" -> totalSeconds += amount * 86400 * 30;
                case "a" -> totalSeconds += amount * 86400 * 365;
            }
        }

        return found ? Duration.ofSeconds(totalSeconds) : null;
    }
}
