package com.studentorgs.portal.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void includes_officerAdmin_includesMemberAndItself() {
        assertThat(Role.OFFICER_ADMIN.includes(Role.MEMBER)).isTrue();
        assertThat(Role.OFFICER_ADMIN.includes(Role.OFFICER_ADMIN)).isTrue();
        assertThat(Role.MEMBER.includes(Role.MEMBER)).isTrue();
    }

    @Test
    void includes_member_doesNotIncludeOfficerAdmin() {
        assertThat(Role.MEMBER.includes(Role.OFFICER_ADMIN)).isFalse();
    }

    @Test
    void values_onlyTwoRoles() {
        assertThat(Role.values()).containsExactly(Role.MEMBER, Role.OFFICER_ADMIN);
    }

    @Test
    void authority_officerAdmin_hasRolePrefix() {
        assertThat(Role.OFFICER_ADMIN.authority()).isEqualTo("ROLE_OFFICER_ADMIN");
    }
}
