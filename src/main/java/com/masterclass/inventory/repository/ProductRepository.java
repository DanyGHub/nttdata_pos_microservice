package com.masterclass.inventory.repository;

import com.masterclass.inventory.entity.Product;
import com.masterclass.inventory.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    List<Product> findByCurrentStockLessThanEqualAndStatus(int reorderLevel, ProductStatus status);

    @Query("select p from Product p where lower(p.name) like lower(concat('%', :term, '%')) or lower(p.sku) like lower(concat('%', :term, '%'))")
    List<Product> search(@Param("term") String term);

    @Query("select p from Product p join fetch p.category where p.status = :status and p.currentStock <= p.reorderLevel")
    List<Product> findLowStock(@Param("status") ProductStatus status);

    @Query("select p from Product p join fetch p.category")
    List<Product> findAllWithCategory();
}
