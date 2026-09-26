package com.b2binventory.dto;

import com.b2binventory.domain.Category;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    
    private Long id;
    private String name;
    private String description;
    private String iconUrl;
    private String status;
    private Long businessId;
    private int productCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static CategoryResponse fromEntity(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setDescription(category.getDescription());
        response.setIconUrl(category.getIconUrl());
        response.setStatus(category.getStatus());
        response.setBusinessId(category.getBusiness().getId());
        response.setProductCount(category.getProductCount());
        response.setCreatedAt(category.getCreatedAt());
        response.setUpdatedAt(category.getUpdatedAt());
        return response;
    }
}
