package w4cash.settings;

import org.springframework.data.jpa.repository.JpaRepository;

interface SettingsRepository extends JpaRepository<Settings, Long> {
}
