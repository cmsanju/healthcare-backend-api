package com.healthcare.repository;

import com.healthcare.model.Document;
import com.healthcare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUserOrderByUploadedAtDesc(User user);
    List<Document> findByStatus(String status);
}
