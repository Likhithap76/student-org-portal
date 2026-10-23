package com.studentorgs.portal.reporting.service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.studentorgs.portal.reporting.dto.EngagementRow;

/**
 * RFC 4180 CSV (CRLF line ends, quotes doubled). Text cells starting with {@code = + - @} get a leading
 * apostrophe so spreadsheet apps don't run them as formulas (CSV injection).
 */
public final class CsvWriter {

    static final String HEADER = "user_id,display_name,email,role,status,events_attended,tasks_completed";
    private static final String CRLF = "\r\n";

    private CsvWriter() {
    }

    public static String engagement(List<EngagementRow> rows) {
        return Stream.concat(Stream.of(HEADER), rows.stream().map(CsvWriter::line))
                .collect(Collectors.joining(CRLF, "", CRLF));
    }

    private static String line(EngagementRow r) {
        return String.join(",",
                String.valueOf(r.userId()),
                text(r.displayName()),
                text(r.email()),
                r.role().name().toLowerCase(Locale.ROOT),
                r.status().name().toLowerCase(Locale.ROOT),
                String.valueOf(r.eventsAttended()),
                String.valueOf(r.tasksCompleted()));
    }

    static String text(String value) {
        if (value == null) {
            return "";
        }
        String safe = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        boolean needsQuotes = safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")
                || !safe.equals(safe.strip());
        return needsQuotes ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }
}
