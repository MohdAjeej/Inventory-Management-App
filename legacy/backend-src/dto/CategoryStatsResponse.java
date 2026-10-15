package com.b2binventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryStatsResponse {
    
    private long totalCategories;
    private long activeCategories;
    private long inactiveCategories;
    private long categoriesInUse;
    private long totalProducts;
}
