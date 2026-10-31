package br.com.lucena.erp.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FacultySettingsRepository extends JpaRepository<FacultySettings, UUID> {
}
