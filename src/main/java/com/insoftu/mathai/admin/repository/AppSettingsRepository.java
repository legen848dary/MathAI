package com.insoftu.mathai.admin.repository;

import com.insoftu.mathai.admin.model.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppSettingsRepository extends JpaRepository<AppSettings, String> {
}
