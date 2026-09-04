package w4cash.receipt;

import org.springframework.data.jpa.repository.JpaRepository;

interface ActiveCashRepository extends JpaRepository<ActiveCash, Long> {

}