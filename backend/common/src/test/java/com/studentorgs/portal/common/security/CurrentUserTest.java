package com.studentorgs.portal.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_authenticatedUser_returnsPrincipal() {
        AuthenticatedUser user = new AuthenticatedUser(7L, "o@uta.edu", "O", Role.OFFICER_ADMIN, UserStatus.ACTIVE);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));

        assertThat(CurrentUser.id()).isEqualTo(7L);
        assertThat(CurrentUser.role()).isEqualTo(Role.OFFICER_ADMIN);
        assertThat(CurrentUser.hasRole(Role.MEMBER)).isTrue();
        assertThat(CurrentUser.hasRole(Role.OFFICER_ADMIN)).isTrue();
        assertThat(CurrentUser.isActive()).isTrue();
    }

    @Test
    void hasRole_member_doesNotHaveOfficerAdmin() {
        AuthenticatedUser user = new AuthenticatedUser(8L, "m@uta.edu", "M", Role.MEMBER, UserStatus.ACTIVE);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));

        assertThat(CurrentUser.hasRole(Role.OFFICER_ADMIN)).isFalse();
    }

    @Test
    void get_noAuthentication_throws() {
        assertThatThrownBy(CurrentUser::get).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void get_internalCaller_throws() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("internal-service", null, List.of()));

        assertThatThrownBy(CurrentUser::get).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
