package com.b2binventory.security;

public enum Permission {
    PRODUCT_VIEW("View products and stock"),
    PRODUCT_CREATE("Create products"),
    PRODUCT_EDIT("Edit products"),
    PRODUCT_ARCHIVE("Archive products"),
    STOCK_ADD("Add stock"),
    STOCK_REDUCE("Reduce stock"),
    STOCK_ADJUST("Adjust stock, damage and reservations"),
    STOCK_TRANSFER("Transfer stock between warehouses"),
    STOCK_COUNT("Run stock counts"),
    REPORT_VIEW("View reports"),
    REPORT_EXPORT("Export reports"),
    CATEGORY_MANAGE("Manage categories, attributes and units"),
    WAREHOUSE_MANAGE("Manage warehouses and locations"),
    EMPLOYEE_MANAGE("Manage employees and permissions"),
    BUSINESS_SETTINGS("Edit business profile and settings"),
    SALE_RECORD("Record sales, take payments and manage customers"),
    SALE_CANCEL("Cancel sales and record returns"),
    ANALYTICS_VIEW("View sales, profit and product analytics"),
    PURCHASE_MANAGE("Record purchases and manage suppliers");

    private final String label;

    Permission(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
