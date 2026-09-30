package com.masterclass.inventory.service;

import com.masterclass.inventory.dto.ProductDtos.*;
import com.masterclass.inventory.entity.*;
import com.masterclass.inventory.exception.BusinessException;
import com.masterclass.inventory.exception.NotFoundException;
import com.masterclass.inventory.mapper.ProductMapper;
import com.masterclass.inventory.repository.CategoryRepository;
import com.masterclass.inventory.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository products;

    @Mock
    private CategoryRepository categories;

    @Mock
    private ProductMapper mapper;

    @InjectMocks
    private ProductService service;

    private Category testCategory;
    private Product testProduct;
    private ProductRequest testRequest;
    private ProductResponse testResponse;

    @BeforeEach
    void setUp() {
        testCategory = Category.builder()
                .id(1L)
                .name("Produce")
                .description("Fresh fruits")
                .build();

        testProduct = Product.builder()
                .id(10L)
                .sku("APL-001")
                .name("Red Apples")
                .description("1kg bag")
                .category(testCategory)
                .unitPrice(new BigDecimal("2.99"))
                .currentStock(100)
                .reorderLevel(20)
                .status(ProductStatus.ACTIVE)
                .build();

        testRequest = new ProductRequest(
                "APL-001",
                "Red Apples",
                "1kg bag",
                1L,
                new BigDecimal("2.99"),
                100,
                20,
                ProductStatus.ACTIVE
        );

        testResponse = new ProductResponse(
                10L,
                "APL-001",
                "Red Apples",
                "1kg bag",
                1L,
                "Produce",
                new BigDecimal("2.99"),
                100,
                20,
                ProductStatus.ACTIVE
        );
    }

    @Test
    @DisplayName("Should create product successfully when SKU is unique and category exists")
    void create_success() {
        when(products.existsBySku("APL-001")).thenReturn(false);
        when(categories.findById(1L)).thenReturn(Optional.of(testCategory));
        when(mapper.toEntity(testRequest)).thenReturn(testProduct);
        when(products.save(testProduct)).thenReturn(testProduct);
        when(mapper.toResponse(testProduct)).thenReturn(testResponse);

        ProductResponse response = service.create(testRequest);

        assertThat(response).isNotNull();
        assertThat(response.sku()).isEqualTo("APL-001");
        assertThat(response.categoryName()).isEqualTo("Produce");
        verify(products).save(testProduct);
    }

    @Test
    @DisplayName("Should throw BusinessException when creating product with existing SKU")
    void create_duplicateSku_throwsBusinessException() {
        when(products.existsBySku("APL-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(testRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SKU already exists");

        verify(products, never()).save(any());
    }

    @Test
    @DisplayName("Should throw NotFoundException when category does not exist")
    void create_categoryNotFound_throwsNotFoundException() {
        when(products.existsBySku("APL-001")).thenReturn(false);
        when(categories.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(testRequest))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found");

        verify(products, never()).save(any());
    }

    @Test
    @DisplayName("Should list all products when search term is null or blank")
    void list_withoutTerm_returnsAllProducts() {
        when(products.findAll()).thenReturn(List.of(testProduct));
        when(mapper.toResponse(testProduct)).thenReturn(testResponse);

        List<ProductResponse> all = service.list(null);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).name()).isEqualTo("Red Apples");

        List<ProductResponse> allBlank = service.list("   ");
        assertThat(allBlank).hasSize(1);

        verify(products, times(2)).findAll();
        verify(products, never()).search(any());
    }

    @Test
    @DisplayName("Should search products by term when term is provided")
    void list_withTerm_returnsMatchingProducts() {
        when(products.search("apple")).thenReturn(List.of(testProduct));
        when(mapper.toResponse(testProduct)).thenReturn(testResponse);

        List<ProductResponse> results = service.list("apple");

        assertThat(results).hasSize(1);
        verify(products).search("apple");
        verify(products, never()).findAll();
    }

    @Test
    @DisplayName("Should get product by id successfully")
    void get_success() {
        when(products.findById(10L)).thenReturn(Optional.of(testProduct));
        when(mapper.toResponse(testProduct)).thenReturn(testResponse);

        ProductResponse response = service.get(10L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Should throw NotFoundException when getting non-existent product")
    void get_notFound_throwsException() {
        when(products.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    @DisplayName("Should update product fields successfully")
    void update_success() {
        ProductRequest updateRequest = new ProductRequest(
                "APL-002",
                "Green Apples",
                "2kg bag",
                1L,
                new BigDecimal("4.50"),
                50,
                15,
                ProductStatus.ACTIVE
        );

        when(products.findById(10L)).thenReturn(Optional.of(testProduct));
        when(products.findBySku("APL-002")).thenReturn(Optional.empty());
        when(categories.findById(1L)).thenReturn(Optional.of(testCategory));
        when(mapper.toResponse(testProduct)).thenReturn(new ProductResponse(
                10L, "APL-002", "Green Apples", "2kg bag", 1L, "Produce",
                new BigDecimal("4.50"), 50, 15, ProductStatus.ACTIVE
        ));

        ProductResponse response = service.update(10L, updateRequest);

        assertThat(response.sku()).isEqualTo("APL-002");
        assertThat(response.name()).isEqualTo("Green Apples");
        assertThat(testProduct.getSku()).isEqualTo("APL-002");
        assertThat(testProduct.getUnitPrice()).isEqualTo(new BigDecimal("4.50"));
    }

    @Test
    @DisplayName("Should deactivate product on delete (logical delete)")
    void delete_deactivatesProduct() {
        when(products.findById(10L)).thenReturn(Optional.of(testProduct));

        assertThat(testProduct.getStatus()).isEqualTo(ProductStatus.ACTIVE);

        service.delete(10L);

        assertThat(testProduct.getStatus()).isEqualTo(ProductStatus.INACTIVE);
    }
}
