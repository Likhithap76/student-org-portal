package com.studentorgs.portal.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studentorgs.portal.content.domain.Announcement;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
}
