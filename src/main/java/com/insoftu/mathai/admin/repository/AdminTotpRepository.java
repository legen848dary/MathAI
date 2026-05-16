package com.insoftu.mathai.admin.repository;

import com.insoftu.mathai.admin.model.AdminTotp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminTotpRepository extends JpaRepository<AdminTotp, UUID> {
    Optional<AdminTotp> findByAdminUserId(UUID adminUserId);
    void deleteByAdminUserId(UUID adminUserId);
}
