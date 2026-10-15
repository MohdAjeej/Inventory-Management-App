package com.b2binventory.service;

import com.b2binventory.dto.CatalogDtos.*;
import com.b2binventory.entity.AttributeDefinition;
import com.b2binventory.entity.FieldType;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.repository.AttributeDefinitionRepository;
import com.b2binventory.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class AttributeService {

    private final AttributeDefinitionRepository repository;
    private final CategoryService categoryService;
    private final AuditService audit;

    public AttributeService(AttributeDefinitionRepository repository, CategoryService categoryService, AuditService audit) {
        this.repository = repository;
        this.categoryService = categoryService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AttributeResponse> list(Long categoryId, boolean includeArchived) {
        return repository.findAllForBusiness(CurrentUser.get().businessId()).stream()
                .filter(a -> includeArchived || !a.isArchived())
                .filter(a -> categoryId == null
                        || (a.getCategory() != null && a.getCategory().getId().equals(categoryId)))
                .map(AttributeService::toResponse)
                .toList();
    }

    @Transactional
    public AttributeResponse create(AttributeRequest request) {
        CurrentUser me = CurrentUser.get();
        AttributeDefinition a = new AttributeDefinition();
        a.setBusinessId(me.businessId());
        a.setKey(request.key());
        apply(a, request);
        repository.save(a);
        audit.record(me.businessId(), me.membershipId(), "ATTRIBUTE_CREATED", "AttributeDefinition", a.getId(),
                Map.of("key", a.getKey(), "type", a.getFieldType()));
        return toResponse(a);
    }

    /** The key is immutable once created because stored product values are keyed by it. */
    @Transactional
    public AttributeResponse update(Long id, AttributeRequest request) {
        CurrentUser me = CurrentUser.get();
        AttributeDefinition a = find(id);
        if (!a.getKey().equals(request.key())) {
            throw new BadRequestException("The key of an existing field cannot be changed");
        }
        apply(a, request);
        audit.record(me.businessId(), me.membershipId(), "ATTRIBUTE_UPDATED", "AttributeDefinition", id,
                Map.of("key", a.getKey()));
        return toResponse(a);
    }

    @Transactional
    public AttributeResponse setArchived(Long id, boolean archived) {
        CurrentUser me = CurrentUser.get();
        AttributeDefinition a = find(id);
        a.setArchived(archived);
        audit.record(me.businessId(), me.membershipId(), archived ? "ATTRIBUTE_ARCHIVED" : "ATTRIBUTE_RESTORED",
                "AttributeDefinition", id, Map.of("key", a.getKey()));
        return toResponse(a);
    }

    private void apply(AttributeDefinition a, AttributeRequest request) {
        a.setLabel(request.label().trim());
        a.setFieldType(request.fieldType());
        a.setRequired(request.required());
        a.setSortOrder(request.sortOrder());
        List<String> options = request.options() == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(request.options().stream().map(String::trim).toList()));
        boolean needsOptions = request.fieldType() == FieldType.DROPDOWN || request.fieldType() == FieldType.MULTI_SELECT;
        if (needsOptions && options.isEmpty()) {
            throw new BadRequestException("Validation failed", Map.of("options", "Add at least one option"));
        }
        a.setOptions(needsOptions ? options : new ArrayList<>());
        a.setCategory(request.categoryId() == null ? null : categoryService.find(request.categoryId()));
    }

    private AttributeDefinition find(Long id) {
        return repository.findByIdAndBusinessId(id, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Attribute", id));
    }

    public static AttributeResponse toResponse(AttributeDefinition a) {
        return new AttributeResponse(a.getId(), a.getKey(), a.getLabel(), a.getFieldType(), a.isRequired(),
                a.getOptions(), a.getSortOrder(),
                a.getCategory() != null ? a.getCategory().getId() : null,
                a.getCategory() != null ? a.getCategory().getName() : null,
                a.isArchived());
    }
}
