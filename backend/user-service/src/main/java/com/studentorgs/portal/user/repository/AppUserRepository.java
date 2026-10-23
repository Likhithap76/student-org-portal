package com.studentorgs.portal.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.domain.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRole(Role role);

    long countByRoleAndStatus(Role role, UserStatus status);

    @Query("select u.id from AppUser u where u.status = :status order by u.id")
    List<Long> findIdsByStatus(@Param("status") UserStatus status);

    // Roster filters. Each one adds a condition only when its value is given: PostgreSQL rejects
    // "? is null" checks on untyped null parameters, so optional filters are built as Specifications.

    static Specification<AppUser> hasStatus(UserStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<AppUser> hasRole(Role role) {
        return (root, query, cb) -> role == null ? null : cb.equal(root.get("role"), role);
    }

    /** {@code pattern} is a lowercase LIKE pattern (e.g. {@code %ana%}) or null for no text filter. */
    static Specification<AppUser> nameOrEmailLike(String pattern) {
        return (root, query, cb) -> pattern == null ? null : cb.or(
                cb.like(cb.lower(root.get("displayName")), pattern, '\\'),
                cb.like(cb.lower(root.get("email")), pattern, '\\'));
    }
}
