package com.masterclass.inventory.mapper;

import com.masterclass.inventory.dto.SaleDtos;
import com.masterclass.inventory.entity.Sale;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-30T00:13:50-0600",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class SaleMapperImpl implements SaleMapper {

    @Override
    public SaleDtos.SaleResponse toResponse(Sale sale) {
        if ( sale == null ) {
            return null;
        }

        Long id = null;
        OffsetDateTime saleDate = null;
        BigDecimal totalAmount = null;

        id = sale.getId();
        saleDate = sale.getSaleDate();
        totalAmount = sale.getTotalAmount();

        List<SaleDtos.SaleDetailResponse> details = toDetailResponses(sale.getDetails());

        SaleDtos.SaleResponse saleResponse = new SaleDtos.SaleResponse( id, saleDate, totalAmount, details );

        return saleResponse;
    }
}
