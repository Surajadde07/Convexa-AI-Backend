package com.convexa.ai.convexa_ai_backend.repository;

import com.convexa.ai.convexa_ai_backend.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByCompanySlug(String companySlug);

    @Query("SELECT c FROM Company c WHERE c.companySlug = :slug")
    Optional<Company> findBySlug(@Param("slug") String slug);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Company c WHERE c.companySlug = :slug")
    boolean existsBySlug(@Param("slug") String slug);
}
