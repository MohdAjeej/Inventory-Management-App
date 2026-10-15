package com.b2binventory.service;

import com.b2binventory.entity.BusinessSettings;
import com.b2binventory.entity.Unit;
import com.b2binventory.entity.Warehouse;
import com.b2binventory.repository.BusinessSettingsRepository;
import com.b2binventory.repository.UnitRepository;
import com.b2binventory.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Seeds a new workspace with neutral defaults that suit any industry. Everything is editable afterwards. */
@Service
public class OnboardingService {

    private static final String[][] DEFAULT_UNITS = {
            {"pcs", "Pieces", "0"},
            {"box", "Boxes", "0"},
            {"pack", "Packs", "0"},
            {"set", "Sets", "0"},
            {"kg", "Kilograms", "3"},
            {"g", "Grams", "0"},
            {"l", "Litres", "3"},
            {"ml", "Millilitres", "0"},
            {"m", "Metres", "2"},
            {"cm", "Centimetres", "1"},
            {"ft", "Feet", "2"},
            {"sqm", "Square metres", "2"},
    };

    private final BusinessSettingsRepository settingsRepository;
    private final UnitRepository unitRepository;
    private final WarehouseRepository warehouseRepository;

    public OnboardingService(BusinessSettingsRepository settingsRepository, UnitRepository unitRepository,
                             WarehouseRepository warehouseRepository) {
        this.settingsRepository = settingsRepository;
        this.unitRepository = unitRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void seed(Long businessId, String timezone) {
        BusinessSettings settings = new BusinessSettings();
        settings.setBusinessId(businessId);
        if (timezone != null && !timezone.isBlank()) settings.setTimezone(BusinessService.validZone(timezone));
        settingsRepository.save(settings);

        for (String[] u : DEFAULT_UNITS) {
            Unit unit = new Unit();
            unit.setBusinessId(businessId);
            unit.setCode(u[0]);
            unit.setName(u[1]);
            unit.setDecimals(Integer.parseInt(u[2]));
            unitRepository.save(unit);
        }

        Warehouse main = new Warehouse();
        main.setBusinessId(businessId);
        main.setName("Main Warehouse");
        main.setCode("MAIN");
        main.setDefaultWarehouse(true);
        warehouseRepository.save(main);
    }
}
