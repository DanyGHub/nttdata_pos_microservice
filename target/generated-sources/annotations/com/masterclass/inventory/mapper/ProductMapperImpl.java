package com.masterclass.inventory.mapper;

import com.masterclass.inventory.dto.ProductDtos;
import com.masterclass.inventory.entity.Category;
import com.masterclass.inventory.entity.Product;
import com.masterclass.inventory.entity.ProductStatus;
import java.math.BigDecimal;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-29T23:45:59-0600",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class ProductMapperImpl implements ProductMapper {

    @Override
    public ProductDtos.ProductResponse toResponse(Product product) {
        if ( product == null ) {
            return null;
        }

        Long categoryId = null;
        String categoryName = null;
        Long id = null;
        String sku = null;
        String name = null;
        String description = null;
        BigDecimal unitPrice = null;
        int currentStock = 0;
        int reorderLevel = 0;
        ProductStatus status = null;

        categoryId = productCategoryId( product );
        categoryName = productCategoryName( product );
        id = product.getId();
        sku = product.getSku();
        name = product.getName();
        description = product.getDescription();
        unitPrice = product.getUnitPrice();
        currentStock = product.getCurrentStock();
        reorderLevel = product.getReorderLevel();
        status = product.getStatus();

        ProductDtos.ProductResponse productResponse = new ProductDtos.ProductResponse( id, sku, name, description, categoryId, categoryName, unitPrice, currentStock, reorderLevel, status );

        return productResponse;
    }

    @Override
    public Product toEntity(ProductDtos.ProductRequest request) {
        if ( request == null ) {
            return null;
        }

        Product.ProductBuilder product = Product.builder();

        product.sku( request.sku() );
        product.name( request.name() );
        product.description( request.description() );
        product.unitPrice( request.unitPrice() );
        product.currentStock( request.currentStock() );
        product.reorderLevel( request.reorderLevel() );
        product.status( request.status() );

        return product.build();
    }

    private Long productCategoryId(Product product) {
        Category category = product.getCategory();
        if ( category == null ) {
            return null;
        }
        return category.getId();
    }

    private String productCategoryName(Product product) {
        Category category = product.getCategory();
        if ( category == null ) {
            return null;
        }
        return category.getName();
    }
}
