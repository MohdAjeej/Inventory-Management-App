package com.b2binventory.service;

import com.b2binventory.dto.BusinessDtos.*;
import com.b2binventory.entity.Business;
import com.b2binventory.entity.BusinessSettings;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.repository.BusinessRepository;
import com.b2binventory.repository.BusinessSettingsRepository;
import com.b2binventory.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static com.b2binventory.service.AuthService.blankToNull;

@Service
public class BusinessService {

    private final BusinessRepository businesses;
    private final BusinessSettingsRepository settingsRepository;
    private final FileStorageService files;
    private final AuditService audit;

    public BusinessService(BusinessRepository businesses, BusinessSettingsRepository settingsRepository,
                           FileStorageService files, AuditService audit) {
        this.businesses = businesses;
        this.settingsRepository = settingsRepository;
        this.files = files;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public BusinessResponse get() {
        return toResponse(current());
    }

    @Transactional
    public BusinessResponse update(UpdateBusinessRequest request) {
        Business b = current();
        b.setName(request.name().trim());
        b.setBusinessType(blankToNull(request.businessType()));
        b.setEmail(blankToNull(request.email()));
        b.setPhone(blankToNull(request.phone()));
        b.setAddress(blankToNull(request.address()));
        b.setCity(blankToNull(request.city()));
        b.setState(blankToNull(request.state()));
        b.setCountry(blankToNull(request.country()));
        b.setPostalCode(blankToNull(request.postalCode()));
        if (request.taxId() != null) b.setTaxId(blankToNull(request.taxId()));
        CurrentUser me = CurrentUser.get();
        audit.record(me.businessId(), me.membershipId(), "BUSINESS_UPDATED", "Business", b.getId(), Map.of("name", b.getName()));
        return toResponse(b);
    }

    @Transactional
    public BusinessResponse uploadLogo(MultipartFile file) {
        Business b = current();
        String previous = b.getLogoUrl();
        b.setLogoUrl(files.storeImage(file));
        files.deleteQuietly(previous);
        return toResponse(b);
    }

    @Transactional(readOnly = true)
    public SettingsResponse settings() {
        return toResponse(settingsEntity(CurrentUser.get().businessId()));
    }

    @Transactional
    public SettingsResponse updateSettings(UpdateSettingsRequest r) {
        CurrentUser me = CurrentUser.get();
        BusinessSettings s = settingsEntity(me.businessId());
        s.setAllowNegativeStock(r.allowNegativeStock());
        s.setDefaultLowStockThreshold(r.defaultLowStockThreshold());
        s.setTrackBatches(r.trackBatches());
        s.setTrackExpiry(r.trackExpiry());
        s.setTrackSerials(r.trackSerials());
        s.setTrackDamagedReserved(r.trackDamagedReserved());
        s.setNotifyLowStock(r.notifyLowStock());
        if (r.timezone() != null) s.setTimezone(validZone(r.timezone()));
        if (r.currencyCode() != null) s.setCurrencyCode(r.currencyCode());
        audit.record(me.businessId(), me.membershipId(), "SETTINGS_UPDATED", "BusinessSettings", me.businessId(),
                Map.of("allowNegativeStock", r.allowNegativeStock(), "defaultLowStockThreshold", r.defaultLowStockThreshold()));
        return toResponse(s);
    }

    /** Settings lookup used by other services; never trusts a client-supplied business id. */
    @Transactional(readOnly = true)
    public BusinessSettings settingsEntity(Long businessId) {
        return settingsRepository.findById(businessId).orElseGet(() -> {
            BusinessSettings s = new BusinessSettings();
            s.setBusinessId(businessId);
            return s;
        });
    }

    /** The business's timezone, falling back to UTC for an unknown value. */
    @Transactional(readOnly = true)
    public ZoneId zone(Long businessId) {
        try {
            return ZoneId.of(settingsEntity(businessId).getTimezone());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }

    public static String validZone(String zone) {
        try {
            return ZoneId.of(zone.trim()).getId();
        } catch (DateTimeException e) {
            throw new BadRequestException("Validation failed", Map.of("timezone", "Unknown timezone"));
        }
    }

    private Business current() {
        return businesses.findById(CurrentUser.get().businessId()).orElseThrow();
    }

    private static BusinessResponse toResponse(Business b) {
        return new BusinessResponse(b.getId(), b.getName(), b.getBusinessType(), b.getLogoUrl(), b.getEmail(), b.getPhone(),
                b.getAddress(), b.getCity(), b.getState(), b.getCountry(), b.getPostalCode(), b.getCreatedAt(),
                b.getBusinessCode(), b.getTaxId());
    }

    private static SettingsResponse toResponse(BusinessSettings s) {
        return new SettingsResponse(s.isAllowNegativeStock(), s.getDefaultLowStockThreshold(), s.isTrackBatches(),
                s.isTrackExpiry(), s.isTrackSerials(), s.isTrackDamagedReserved(), s.isNotifyLowStock(), s.getTimezone(),
                s.getCurrencyCode());
    }
}
