package com.healthcare.repository;

import com.healthcare.model.Report;
import com.healthcare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByUserOrderByGeneratedAtDesc(User user);
    List<Report> findByDocumentId(Long documentId);
}
