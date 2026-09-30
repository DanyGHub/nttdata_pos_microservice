package com.masterclass.inventory.mapper;

import com.masterclass.inventory.dto.CategoryDtos;
import com.masterclass.inventory.entity.Category;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-29T22:05:06-0600",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class CategoryMapperImpl implements CategoryMapper {

    @Override
    public CategoryDtos.CategoryResponse toResponse(Category c) {
        if ( c == null ) {
            return null;
        }

        Long id = null;
        String name = null;
        String description = null;

        id = c.getId();
        name = c.getName();
        description = c.getDescription();

        CategoryDtos.CategoryResponse categoryResponse = new CategoryDtos.CategoryResponse( id, name, description );

        return categoryResponse;
    }

    @Override
    public Category toEntity(CategoryDtos.CategoryRequest r) {
        if ( r == null ) {
            return null;
        }

        Category.CategoryBuilder category = Category.builder();

        category.name( r.name() );
        category.description( r.description() );

        return category.build();
    }
}
