package com.studentorgs.portal.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.studentorgs.portal.ai.client.TaskDirectoryClient.CommitteeMember;

class AssigneeMatcherTest {

    private static final List<CommitteeMember> MEMBERS = List.of(
            new CommitteeMember(2L, "Ana Lopez"),
            new CommitteeMember(3L, "Bob Lee"),
            new CommitteeMember(4L, "Ana Kim"),
            new CommitteeMember(5L, "Cher"));

    @Test
    void match_fullNameAnyCaseAndSpacing_returnsId() {
        assertThat(AssigneeMatcher.match("  ana   LOPEZ ", MEMBERS)).isEqualTo(2L);
    }

    @Test
    void match_uniqueFirstName_returnsId() {
        assertThat(AssigneeMatcher.match("bob", MEMBERS)).isEqualTo(3L);
        assertThat(AssigneeMatcher.match("Cher", MEMBERS)).isEqualTo(5L);
    }

    @Test
    void match_sharedFirstName_returnsNull() {
        assertThat(AssigneeMatcher.match("Ana", MEMBERS)).isNull();
    }

    @Test
    void match_unknownOrPartialFullName_returnsNull() {
        assertThat(AssigneeMatcher.match("Zed", MEMBERS)).isNull();
        assertThat(AssigneeMatcher.match("Ana Lo", MEMBERS)).isNull();
    }

    @Test
    void match_noNameOrNoMembers_returnsNull() {
        assertThat(AssigneeMatcher.match(null, MEMBERS)).isNull();
        assertThat(AssigneeMatcher.match(" ", MEMBERS)).isNull();
        assertThat(AssigneeMatcher.match("Bob", List.of())).isNull();
    }

    @Test
    void match_duplicateFullNames_returnsNull() {
        List<CommitteeMember> twins = List.of(new CommitteeMember(2L, "Sam Roe"), new CommitteeMember(3L, "Sam Roe"));

        assertThat(AssigneeMatcher.match("Sam Roe", twins)).isNull();
        assertThat(AssigneeMatcher.match("Sam", twins)).isNull();
    }
}
