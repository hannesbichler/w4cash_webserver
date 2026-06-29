package w4cash.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface PersonPhoneRepository extends JpaRepository<PersonPhone, String> {
    Optional<PersonPhone> findByPhone(String phone);
}
