package com.b2binventory;

import com.b2binventory.support.IntegrationTest;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.b2binventory.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class ReportExportTest extends IntegrationTest {

    @Test
    void inventoryAndMovementReportsExportAsCsvAndXlsx() throws Exception {
        Session admin = registerBusiness("Report Co");
        long v = defaultVariant(post(admin, "/api/products", Map.of("name", "=Formula, Inc", "minStock", 2,
                "openingStock", Map.of("quantity", 1))).body());
        post(admin, "/api/products", product("Plenty", 50));
        post(admin, "/api/stock/operations", op("ADD", v, 1));

        Response low = get(admin, "/api/reports/low-stock");
        assertThat(low.get("totalElements").asInt()).isEqualTo(1);

        MvcResult csv = mvc.perform(MockMvcRequestBuilders.get("/api/reports/inventory/export?format=csv")
                .header("Authorization", "Bearer " + admin.token())).andReturn();
        assertThat(csv.getResponse().getStatus()).isEqualTo(200);
        assertThat(csv.getResponse().getHeader("Content-Disposition")).contains(".csv");
        String text = new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿Product,Variant,SKU");
        assertThat(text).contains("\"'=Formula, Inc\"");

        MvcResult xlsx = mvc.perform(MockMvcRequestBuilders.get("/api/reports/movements/export?format=xlsx")
                .header("Authorization", "Bearer " + admin.token())).andReturn();
        assertThat(xlsx.getResponse().getStatus()).isEqualTo(200);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Date (UTC)");
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
        }

        Session staff = addEmployee(admin, "INVENTORY_MANAGER");
        assertThat(mvc.perform(MockMvcRequestBuilders.get("/api/reports/inventory/export?format=csv")
                .header("Authorization", "Bearer " + staff.token())).andReturn().getResponse().getStatus()).isEqualTo(200);
        Session basic = addEmployee(admin, "STAFF");
        assertThat(mvc.perform(MockMvcRequestBuilders.get("/api/reports/inventory/export?format=csv")
                .header("Authorization", "Bearer " + basic.token())).andReturn().getResponse().getStatus()).isEqualTo(403);
    }
}
