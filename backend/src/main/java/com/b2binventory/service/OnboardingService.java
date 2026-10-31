package com.b2binventory.service;

import com.b2binventory.entity.BusinessSettings;
import com.b2binventory.entity.Warehouse;
import com.b2binventory.repository.BusinessSettingsRepository;
import com.b2binventory.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a new workspace with neutral defaults that suit any industry. Everything is editable afterwards.
 * Units of measure are not copied: every business shares the standard units and adds its own custom ones.
 */
@Service
public class OnboardingService {

    private final BusinessSettingsRepository settingsRepository;
    private final WarehouseRepository warehouseRepository;

    public OnboardingService(BusinessSettingsRepository settingsRepository, WarehouseRepository warehouseRepository) {
        this.settingsRepository = settingsRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void seed(Long businessId, String timezone) {
        BusinessSettings settings = new BusinessSettings();
        settings.setBusinessId(businessId);
        if (timezone != null && !timezone.isBlank()) settings.setTimezone(BusinessService.validZone(timezone));
        settingsRepository.save(settings);

        Warehouse main = new Warehouse();
        main.setBusinessId(businessId);
        main.setName("Main Warehouse");
        main.setCode("MAIN");
        main.setDefaultWarehouse(true);
        warehouseRepository.save(main);
    }
}
