package com.b2binventory.service;

import com.b2binventory.dto.CatalogDtos.*;
import com.b2binventory.entity.Product;
import com.b2binventory.entity.Unit;
import com.b2binventory.entity.UnitConversion;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.exception.ConflictException;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.repository.ProductRepository;
import com.b2binventory.repository.UnitConversionRepository;
import com.b2binventory.repository.UnitRepository;
import com.b2binventory.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class UnitService {

    private final UnitRepository units;
    private final UnitConversionRepository conversions;
    private final ProductRepository products;
    private final AuditService audit;

    public UnitService(UnitRepository units, UnitConversionRepository conversions, ProductRepository products,
                       AuditService audit) {
        this.units = units;
        this.conversions = conversions;
        this.products = products;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> list(boolean includeArchived) {
        Long businessId = CurrentUser.get().businessId();
        return (includeArchived ? units.findByBusinessIdOrderByNameAsc(businessId)
                : units.findByBusinessIdAndArchivedFalseOrderByNameAsc(businessId))
                .stream().map(UnitService::toResponse).toList();
    }

    @Transactional
    public UnitResponse create(UnitRequest request) {
        CurrentUser me = CurrentUser.get();
        if (units.findByBusinessIdAndCodeIgnoreCase(me.businessId(), request.code().trim()).isPresent()) {
            throw new ConflictException("A unit with this code already exists");
        }
        Unit unit = new Unit();
        unit.setBusinessId(me.businessId());
        apply(unit, request);
        units.save(unit);
        audit.record(me.businessId(), me.membershipId(), "UNIT_CREATED", "Unit", unit.getId(), Map.of("code", unit.getCode()));
        return toResponse(unit);
    }

    @Transactional
    public UnitResponse update(Long id, UnitRequest request) {
        CurrentUser me = CurrentUser.get();
        Unit unit = find(id);
        units.findByBusinessIdAndCodeIgnoreCase(me.businessId(), request.code().trim())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ConflictException("A unit with this code already exists");
                });
        apply(unit, request);
        audit.record(me.businessId(), me.membershipId(), "UNIT_UPDATED", "Unit", id, Map.of("code", unit.getCode()));
        return toResponse(unit);
    }

    @Transactional
    public UnitResponse setArchived(Long id, boolean archived) {
        CurrentUser me = CurrentUser.get();
        Unit unit = find(id);
        unit.setArchived(archived);
        audit.record(me.businessId(), me.membershipId(), archived ? "UNIT_ARCHIVED" : "UNIT_RESTORED", "Unit", id, null);
        return toResponse(unit);
    }

    @Transactional(readOnly = true)
    public List<ConversionResponse> conversions() {
        return conversions.findByBusinessIdOrderByIdAsc(CurrentUser.get().businessId()).stream()
                .map(UnitService::toResponse).toList();
    }

    @Transactional
    public ConversionResponse createConversion(ConversionRequest request) {
        CurrentUser me = CurrentUser.get();
        if (request.fromUnitId().equals(request.toUnitId())) {
            throw new BadRequestException("Choose two different units");
        }
        UnitConversion c = new UnitConversion();
        c.setBusinessId(me.businessId());
        c.setFromUnit(find(request.fromUnitId()));
        c.setToUnit(find(request.toUnitId()));
        c.setFactor(request.factor());
        if (request.productId() != null) {
            Product p = products.findByIdAndBusinessId(request.productId(), me.businessId())
                    .orElseThrow(() -> new NotFoundException("Product", request.productId()));
            c.setProduct(p);
        }
        conversions.save(c);
        audit.record(me.businessId(), me.membershipId(), "UNIT_CONVERSION_CREATED", "UnitConversion", c.getId(),
                Map.of("from", c.getFromUnit().getCode(), "to", c.getToUnit().getCode(), "factor", c.getFactor()));
        return toResponse(c);
    }

    @Transactional
    public void deleteConversion(Long id) {
        CurrentUser me = CurrentUser.get();
        UnitConversion c = conversions.findByIdAndBusinessId(id, me.businessId())
                .orElseThrow(() -> new NotFoundException("Unit conversion", id));
        conversions.delete(c);
        audit.record(me.businessId(), me.membershipId(), "UNIT_CONVERSION_DELETED", "UnitConversion", id, null);
    }

    /** Converts using a direct or reverse conversion, preferring a product-specific one. */
    @Transactional(readOnly = true)
    public ConvertResult convert(BigDecimal quantity, Long fromUnitId, Long toUnitId, Long productId) {
        Long businessId = CurrentUser.get().businessId();
        Unit from = find(fromUnitId);
        Unit to = find(toUnitId);
        if (from.getId().equals(to.getId())) {
            return new ConvertResult(quantity, from.getCode(), quantity, to.getCode());
        }
        BigDecimal factor = factor(businessId, fromUnitId, toUnitId, productId);
        if (factor == null) {
            BigDecimal reverse = factor(businessId, toUnitId, fromUnitId, productId);
            if (reverse == null) throw new NotFoundException("No conversion defined between " + from.getCode() + " and " + to.getCode());
            factor = BigDecimal.ONE.divide(reverse, MathContext.DECIMAL64);
        }
        BigDecimal result = quantity.multiply(factor).setScale(to.getDecimals(), RoundingMode.HALF_UP);
        return new ConvertResult(quantity, from.getCode(), result, to.getCode());
    }

    private BigDecimal factor(Long businessId, Long from, Long to, Long productId) {
        List<UnitConversion> list = conversions.findByBusinessIdAndFromUnitIdAndToUnitId(businessId, from, to);
        return list.stream()
                .filter(c -> productId != null && c.getProduct() != null && c.getProduct().getId().equals(productId))
                .findFirst()
                .or(() -> list.stream().filter(c -> c.getProduct() == null).findFirst())
                .map(UnitConversion::getFactor)
                .orElse(null);
    }

    Unit find(Long id) {
        return units.findByIdAndBusinessId(id, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Unit", id));
    }

    private static void apply(Unit unit, UnitRequest request) {
        unit.setCode(request.code().trim());
        unit.setName(request.name().trim());
        unit.setDecimals(request.decimals());
    }

    public static UnitResponse toResponse(Unit u) {
        return new UnitResponse(u.getId(), u.getCode(), u.getName(), u.getDecimals(), u.isArchived());
    }

    private static ConversionResponse toResponse(UnitConversion c) {
        return new ConversionResponse(c.getId(), toResponse(c.getFromUnit()), toResponse(c.getToUnit()), c.getFactor(),
                c.getProduct() != null ? c.getProduct().getId() : null,
                c.getProduct() != null ? c.getProduct().getName() : null);
    }
}
