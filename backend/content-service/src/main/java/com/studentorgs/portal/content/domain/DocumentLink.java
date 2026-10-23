package com.studentorgs.portal.content.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A link in the document repository ({@code document_link}). Links only; no file uploads. */
@Entity
@Table(name = "document_link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "url", nullable = false, length = 2000)
    private String url;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DocumentLink(String title, String category, String url, Long createdBy, Instant now) {
        this.title = title;
        this.category = category;
        this.url = url;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String title, String category, String url, Instant now) {
        this.title = title;
        this.category = category;
        this.url = url;
        this.updatedAt = now;
    }
}
