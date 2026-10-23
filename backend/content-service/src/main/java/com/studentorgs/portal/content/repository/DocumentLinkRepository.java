package com.studentorgs.portal.content.repository;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.studentorgs.portal.content.domain.DocumentLink;

public interface DocumentLinkRepository extends JpaRepository<DocumentLink, Long>, JpaSpecificationExecutor<DocumentLink> {

    /** Exact category match, ignoring case; no filter when blank. */
    static Specification<DocumentLink> inCategory(String category) {
        return (root, query, cb) -> category == null || category.isBlank() ? null
                : cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase(Locale.ROOT));
    }

    /** Title contains {@code q}, ignoring case; no filter when blank. */
    static Specification<DocumentLink> titleContains(String q) {
        if (q == null || q.isBlank()) {
            return (root, query, cb) -> null;
        }
        String pattern = "%" + q.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\');
    }
}
