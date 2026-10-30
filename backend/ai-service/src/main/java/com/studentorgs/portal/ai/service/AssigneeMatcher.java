package com.studentorgs.portal.ai.service;

import java.util.List;
import java.util.Locale;

import com.studentorgs.portal.ai.client.TaskDirectoryClient.CommitteeMember;

/**
 * Maps a name from the notes to a committee member: exact full name (ignoring case and extra spaces),
 * otherwise a first name that exactly one member has. Anything else → null (the officer picks).
 */
public final class AssigneeMatcher {

    private AssigneeMatcher() {
    }

    public static Long match(String name, List<CommitteeMember> members) {
        String wanted = normalize(name);
        if (wanted.isEmpty() || members.isEmpty()) {
            return null;
        }
        List<CommitteeMember> fullName = members.stream()
                .filter(m -> normalize(m.displayName()).equals(wanted)).toList();
        if (fullName.size() == 1) {
            return fullName.get(0).userId();
        }
        if (!fullName.isEmpty() || wanted.contains(" ")) {
            return null;
        }
        List<CommitteeMember> firstName = members.stream()
                .filter(m -> firstWord(normalize(m.displayName())).equals(wanted)).toList();
        return firstName.size() == 1 ? firstName.get(0).userId() : null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String firstWord(String value) {
        int space = value.indexOf(' ');
        return space < 0 ? value : value.substring(0, space);
    }
}
