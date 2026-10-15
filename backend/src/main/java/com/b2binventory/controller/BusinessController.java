package com.b2binventory.controller;

import com.b2binventory.dto.BusinessDtos.*;
import com.b2binventory.service.BusinessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/business")
@Tag(name = "Business")
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @GetMapping
    public BusinessResponse get() {
        return businessService.get();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('BUSINESS_SETTINGS')")
    public BusinessResponse update(@Valid @RequestBody UpdateBusinessRequest request) {
        return businessService.update(request);
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('BUSINESS_SETTINGS')")
    public BusinessResponse uploadLogo(@RequestPart("file") MultipartFile file) {
        return businessService.uploadLogo(file);
    }

    @GetMapping("/settings")
    public SettingsResponse settings() {
        return businessService.settings();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('BUSINESS_SETTINGS')")
    public SettingsResponse updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
        return businessService.updateSettings(request);
    }
}
