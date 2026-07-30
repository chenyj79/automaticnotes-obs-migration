package com.black.knowledge.repository;

import com.black.knowledge.po.FrameworkCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FrameworkCategoryRepository extends JpaRepository<FrameworkCategory, Long> {
    List<FrameworkCategory> findByFrameworkIdOrderBySortOrderAscIdAsc(Long frameworkId);

    void deleteByFrameworkId(Long frameworkId);
}

