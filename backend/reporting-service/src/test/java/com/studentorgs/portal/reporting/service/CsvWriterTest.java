package com.studentorgs.portal.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.reporting.dto.EngagementRow;

class CsvWriterTest {

    @Test
    void engagement_rows_headerThenCrlfLines() {
        String csv = CsvWriter.engagement(List.of(
                new EngagementRow(2L, "Ana", "ana@uta.edu", Role.OFFICER_ADMIN, UserStatus.ACTIVE, 3, 1),
                new EngagementRow(5L, "Lee, Bo", "bo@uta.edu", Role.MEMBER, UserStatus.INACTIVE, 0, 0)));

        assertThat(csv).isEqualTo(
                "user_id,display_name,email,role,status,events_attended,tasks_completed\r\n"
                        + "2,Ana,ana@uta.edu,officer_admin,active,3,1\r\n"
                        + "5,\"Lee, Bo\",bo@uta.edu,member,inactive,0,0\r\n");
    }

    @Test
    void engagement_noRows_headerOnly() {
        assertThat(CsvWriter.engagement(List.of())).isEqualTo(CsvWriter.HEADER + "\r\n");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Ana|Ana",
            "Say \"hi\"|\"Say \"\"hi\"\"\""})
    void text_escapesQuotes(String input, String expected) {
        assertThat(CsvWriter.text(input)).isEqualTo(expected);
    }

    @Test
    void text_formulaPrefixes_getLeadingApostrophe() {
        assertThat(CsvWriter.text("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(CsvWriter.text("+1 club")).isEqualTo("'+1 club");
        assertThat(CsvWriter.text("-dash")).isEqualTo("'-dash");
        assertThat(CsvWriter.text("@handle")).isEqualTo("'@handle");
    }

    @Test
    void text_lineBreakAndPadding_quoted() {
        assertThat(CsvWriter.text("two\nlines")).isEqualTo("\"two\nlines\"");
        assertThat(CsvWriter.text(" padded ")).isEqualTo("\" padded \"");
        assertThat(CsvWriter.text(null)).isEmpty();
    }
}
