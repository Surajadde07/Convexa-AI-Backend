package com.convexa.ai.convexa_ai_backend.repository;

import com.convexa.ai.convexa_ai_backend.entity.Company;
import com.convexa.ai.convexa_ai_backend.entity.MembershipStatus;
import com.convexa.ai.convexa_ai_backend.entity.OrganizationMembership;
import com.convexa.ai.convexa_ai_backend.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, Long> {

    @Query("SELECT m.company FROM OrganizationMembership m WHERE m.user.id = :userId AND m.status = 'ACTIVE'")
    List<Company> findActiveCompaniesByUserId(@Param("userId") Long userId);

    Optional<OrganizationMembership> findByUserIdAndCompanyId(Long userId, Long companyId);

    @Query("SELECT m FROM OrganizationMembership m WHERE m.user.id = :userId AND m.company.id = :companyId AND m.status = 'ACTIVE'")
    Optional<OrganizationMembership> findActiveMembership(@Param("userId") Long userId, @Param("companyId") Long companyId);

    boolean existsByUserIdAndCompanyId(Long userId, Long companyId);

    List<OrganizationMembership> findByUserId(Long userId);

    long countByCompanyIdAndStatus(Long companyId, MembershipStatus status);

    long countByCompanyIdAndRoleAndStatus(Long companyId, Role role, MembershipStatus status);

    List<OrganizationMembership> findByCompanyIdAndStatus(Long companyId, MembershipStatus status);

    Page<OrganizationMembership> findByCompanyIdAndStatus(Long companyId, MembershipStatus status, Pageable pageable);
}
