package w4cash.product;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProductsRepository extends JpaRepository<Product, Long> {
}
