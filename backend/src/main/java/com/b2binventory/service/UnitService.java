package com.b2binventory.service;

import com.b2binventory.dto.CatalogDtos.*;
import com.b2binventory.entity.Product;
import com.b2binventory.entity.Unit;
import com.b2binventory.entity.UnitCategory;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

    /** Standard units and the business's own units, grouped by category; q matches the name or the code. */
    @Transactional(readOnly = true)
    public List<UnitResponse> list(boolean includeArchived, String q, UnitCategory category) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return units.findVisible(CurrentUser.get().businessId(), includeArchived).stream()
                .filter(u -> category == null || u.getCategory() == category)
                .filter(u -> query.isEmpty() || u.getName().toLowerCase(Locale.ROOT).contains(query)
                        || u.getCode().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(Unit::getCategory)
                        .thenComparing(Unit::isStandard, Comparator.reverseOrder())
                        .thenComparing(Unit::getBaseFactor, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Unit::getName, String.CASE_INSENSITIVE_ORDER))
                .map(UnitService::toResponse).toList();
    }

    public List<UnitCategoryResponse> categories() {
        return Arrays.stream(UnitCategory.values())
                .map(c -> new UnitCategoryResponse(c, c.label(), c.baseUnitCode())).toList();
    }

    @Transactional
    public UnitResponse create(UnitRequest request) {
        CurrentUser me = CurrentUser.get();
        checkUnique(me.businessId(), null, request);
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
        Unit unit = findCustom(id);
        checkUnique(me.businessId(), id, request);
        UnitCategory previousCategory = unit.getCategory();
        BigDecimal previousFactor = unit.getBaseFactor();
        apply(unit, request);
        if (unit.getCategory() != previousCategory || !sameNumber(unit.getBaseFactor(), previousFactor)) {
            // The unit's conversions must still make sense with what it now measures.
            for (UnitConversion c : conversions.findByBusinessIdOrderByIdAsc(me.businessId())) {
                if (!c.getFromUnit().getId().equals(id) && !c.getToUnit().getId().equals(id)) continue;
                if (builtInFactor(c.getFromUnit(), c.getToUnit()) != null || !convertible(c.getFromUnit(), c.getToUnit())) {
                    throw new BadRequestException("Validation failed", Map.of("category", "Delete the conversion 1 "
                            + c.getFromUnit().getCode() + " = " + c.getFactor().stripTrailingZeros().toPlainString() + " "
                            + c.getToUnit().getCode() + " before changing what this unit measures"));
                }
            }
        }
        audit.record(me.businessId(), me.membershipId(), "UNIT_UPDATED", "Unit", id, Map.of("code", unit.getCode()));
        return toResponse(unit);
    }

    @Transactional
    public UnitResponse setArchived(Long id, boolean archived) {
        CurrentUser me = CurrentUser.get();
        Unit unit = findCustom(id);
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
        Unit from = find(request.fromUnitId());
        Unit to = find(request.toUnitId());
        checkConvertible(from, to);
        UnitConversion c = new UnitConversion();
        c.setBusinessId(me.businessId());
        c.setFromUnit(from);
        c.setToUnit(to);
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
        BigDecimal factor = builtInFactor(from, to);
        if (factor == null && !convertible(from, to)) {
            throw new BadRequestException(differentThings(from, to));
        }
        if (factor == null) factor = factor(businessId, fromUnitId, toUnitId, productId);
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

    /** 1 from = factor x to, when both units have a fixed size in the same category (1 KG = 1000 G). */
    static BigDecimal builtInFactor(Unit from, Unit to) {
        if (from.getCategory() != to.getCategory() || from.getCategory() == UnitCategory.OTHER
                || from.getBaseFactor() == null || to.getBaseFactor() == null) {
            return null;
        }
        return from.getBaseFactor().divide(to.getBaseFactor(), MathContext.DECIMAL64);
    }

    /**
     * Whether a business may define a conversion between two units: same category, a container with no fixed size
     * (1 BAG = 50 KG), or a custom unit whose dimension is unknown. Never kilograms to metres.
     */
    static boolean convertible(Unit from, Unit to) {
        return from.getCategory() == to.getCategory() || isContainer(from) || isContainer(to)
                || from.getCategory() == UnitCategory.OTHER || to.getCategory() == UnitCategory.OTHER;
    }

    private static boolean isContainer(Unit u) {
        return u.getCategory() == UnitCategory.COUNT && u.getBaseFactor() == null;
    }

    private static void checkConvertible(Unit from, Unit to) {
        if (from.getId().equals(to.getId())) {
            throw new BadRequestException("Choose two different units");
        }
        BigDecimal builtIn = builtInFactor(from, to);
        if (builtIn != null) {
            throw new BadRequestException(from.getName() + " and " + to.getName() + " already convert automatically: 1 "
                    + from.getCode() + " = " + builtIn.stripTrailingZeros().toPlainString() + " " + to.getCode());
        }
        if (!convertible(from, to)) throw new BadRequestException(differentThings(from, to));
    }

    private static String differentThings(Unit from, Unit to) {
        return from.getName() + " (" + from.getCategory().label().toLowerCase(Locale.ROOT) + ") and " + to.getName() + " ("
                + to.getCategory().label().toLowerCase(Locale.ROOT) + ") measure different things and cannot be converted";
    }

    private static boolean sameNumber(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    /** A code or name may be used once among the business's units and the standard units. */
    private void checkUnique(Long businessId, Long id, UnitRequest request) {
        units.findVisibleByCode(businessId, request.code().trim()).stream()
                .filter(other -> !other.getId().equals(id)).findFirst()
                .ifPresent(other -> {
                    throw new ConflictException(other.isStandard()
                            ? request.code().trim() + " is already a standard unit (" + other.getName() + ")"
                            : "A unit with this code already exists");
                });
        units.findVisibleByName(businessId, request.name().trim()).stream()
                .filter(other -> !other.getId().equals(id)).findFirst()
                .ifPresent(other -> {
                    throw new ConflictException(other.isStandard()
                            ? other.getName() + " is already a standard unit (" + other.getCode() + ")"
                            : "A unit with this name already exists");
                });
    }

    /** A unit the business can use: one of its own or a standard unit. */
    Unit find(Long id) {
        return units.findVisibleById(id, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Unit", id));
    }

    /** One of the business's own units; standard units are shared and cannot be changed. */
    private Unit findCustom(Long id) {
        Unit unit = find(id);
        if (unit.isStandard()) throw new BadRequestException("Standard units can't be changed");
        return unit;
    }

    private static void apply(Unit unit, UnitRequest request) {
        unit.setCode(request.code().trim());
        unit.setName(request.name().trim());
        unit.setDecimals(request.decimals());
        UnitCategory category = request.category() != null ? request.category() : UnitCategory.OTHER;
        unit.setCategory(category);
        // OTHER has no base unit, so a factor would mean nothing.
        unit.setBaseFactor(category == UnitCategory.OTHER ? null : request.baseFactor());
    }

    public static UnitResponse toResponse(Unit u) {
        return new UnitResponse(u.getId(), u.getCode(), u.getName(), u.getDecimals(), u.isArchived(), u.getCategory(),
                u.getBaseFactor() == null ? null : u.getBaseFactor().stripTrailingZeros(), u.isStandard());
    }

    private static ConversionResponse toResponse(UnitConversion c) {
        return new ConversionResponse(c.getId(), toResponse(c.getFromUnit()), toResponse(c.getToUnit()), c.getFactor(),
                c.getProduct() != null ? c.getProduct().getId() : null,
                c.getProduct() != null ? c.getProduct().getName() : null);
    }
}
