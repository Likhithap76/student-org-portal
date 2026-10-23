package com.studentorgs.portal.task.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A committee or project ({@code committee}). Names are unique. */
@Entity
@Table(name = "committee")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Committee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Convert(converter = CommitteeKindConverter.class)
    @Column(name = "kind", nullable = false, length = 20)
    private CommitteeKind kind;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Committee(String name, CommitteeKind kind, String description, Instant createdAt) {
        this.name = name;
        this.kind = kind;
        this.description = description;
        this.createdAt = createdAt;
    }
}
