package com.b2binventory.service;

import com.b2binventory.dto.CatalogDtos.*;
import com.b2binventory.entity.AttributeDefinition;
import com.b2binventory.entity.BusinessSettings;
import com.b2binventory.entity.Category;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.exception.ConflictException;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.repository.AttributeDefinitionRepository;
import com.b2binventory.repository.CategoryRepository;
import com.b2binventory.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CategoryService {

    private static final int MAX_DEPTH = 5;

    private final CategoryRepository categories;
    private final AttributeDefinitionRepository attributes;
    private final BusinessService businessService;
    private final AuditService audit;

    public CategoryService(CategoryRepository categories, AttributeDefinitionRepository attributes,
                           BusinessService businessService, AuditService audit) {
        this.categories = categories;
        this.attributes = attributes;
        this.businessService = businessService;
        this.audit = audit;
    }

    /** Returns the category tree; archived categories are included only when requested. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> tree(boolean includeArchived) {
        Long businessId = CurrentUser.get().businessId();
        List<Category> all = categories.findAllForBusiness(businessId);
        Map<Long, Long> counts = new HashMap<>();
        categories.productCounts(businessId).forEach(r -> counts.put((Long) r[0], (Long) r[1]));
        Map<Long, List<Category>> byParent = new HashMap<>();
        for (Category c : all) {
            if (!includeArchived && c.isArchived()) continue;
            Long parentId = c.getParent() != null ? c.getParent().getId() : 0L;
            byParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(c);
        }
        return build(0L, byParent, counts, "", 0);
    }

    private List<CategoryResponse> build(Long parentId, Map<Long, List<Category>> byParent, Map<Long, Long> counts,
                                         String prefix, int depth) {
        List<CategoryResponse> result = new ArrayList<>();
        for (Category c : byParent.getOrDefault(parentId, List.of())) {
            String path = prefix.isEmpty() ? c.getName() : prefix + " / " + c.getName();
            List<CategoryResponse> children = build(c.getId(), byParent, counts, path, depth + 1);
            result.add(new CategoryResponse(c.getId(), c.getName(), c.getDescription(), parentId == 0L ? null : parentId,
                    path, c.isArchived(), counts.getOrDefault(c.getId(), 0L), depth, children));
        }
        result.sort(Comparator.comparing(r -> r.name().toLowerCase()));
        return result;
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        Category c = find(id);
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(),
                c.getParent() != null ? c.getParent().getId() : null, path(c), c.isArchived(), 0, ancestors(c).size() - 1,
                List.of());
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        CurrentUser me = CurrentUser.get();
        Category c = new Category();
        c.setBusinessId(me.businessId());
        apply(c, request);
        categories.save(c);
        audit.record(me.businessId(), me.membershipId(), "CATEGORY_CREATED", "Category", c.getId(), Map.of("name", c.getName()));
        return get(c.getId());
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        CurrentUser me = CurrentUser.get();
        Category c = find(id);
        apply(c, request);
        audit.record(me.businessId(), me.membershipId(), "CATEGORY_UPDATED", "Category", id, Map.of("name", c.getName()));
        return get(id);
    }

    @Transactional
    public CategoryResponse setArchived(Long id, boolean archived) {
        CurrentUser me = CurrentUser.get();
        Category c = find(id);
        if (archived && categories.existsByParentIdAndArchivedFalse(id)) {
            throw new ConflictException("Archive the subcategories first");
        }
        if (!archived && c.getParent() != null && c.getParent().isArchived()) {
            throw new ConflictException("Restore the parent category first");
        }
        c.setArchived(archived);
        audit.record(me.businessId(), me.membershipId(), archived ? "CATEGORY_ARCHIVED" : "CATEGORY_RESTORED",
                "Category", id, Map.of("name", c.getName()));
        return get(id);
    }

    /** Fields for a product in this category: business-wide fields plus those of the category and its ancestors. */
    @Transactional(readOnly = true)
    public FormSchema formSchema(Long categoryId) {
        Long businessId = CurrentUser.get().businessId();
        List<AttributeDefinition> fields = applicableAttributes(categoryId);
        BusinessSettings s = businessService.settingsEntity(businessId);
        return new FormSchema(categoryId, fields.stream().map(AttributeService::toResponse).toList(),
                s.isTrackBatches(), s.isTrackExpiry(), s.isTrackSerials(), s.isTrackDamagedReserved());
    }

    @Transactional(readOnly = true)
    public List<AttributeDefinition> applicableAttributes(Long categoryId) {
        Long businessId = CurrentUser.get().businessId();
        if (categoryId == null) return attributes.findGlobal(businessId);
        List<Long> ids = ancestors(find(categoryId)).stream().map(Category::getId).toList();
        return attributes.findApplicable(businessId, ids);
    }

    /** The category followed by its descendants (used for filtering products by category). */
    @Transactional(readOnly = true)
    public Set<Long> withDescendants(Long categoryId) {
        Long businessId = CurrentUser.get().businessId();
        find(categoryId);
        List<Category> all = categories.findAllForBusiness(businessId);
        Set<Long> result = new LinkedHashSet<>(List.of(categoryId));
        boolean added = true;
        while (added) {
            added = false;
            for (Category c : all) {
                if (c.getParent() != null && result.contains(c.getParent().getId()) && result.add(c.getId())) {
                    added = true;
                }
            }
        }
        return result;
    }

    public Category find(Long id) {
        return categories.findByIdAndBusinessId(id, CurrentUser.get().businessId())
                .orElseThrow(() -> new NotFoundException("Category", id));
    }

    /** The category and all its ancestors, root last. */
    public List<Category> ancestors(Category c) {
        List<Category> chain = new ArrayList<>();
        Category current = c;
        while (current != null && chain.size() <= MAX_DEPTH + 1) {
            chain.add(current);
            current = current.getParent();
        }
        return chain;
    }

    public String path(Category c) {
        if (c == null) return null;
        List<Category> chain = ancestors(c);
        Collections.reverse(chain);
        return String.join(" / ", chain.stream().map(Category::getName).toList());
    }

    private void apply(Category c, CategoryRequest request) {
        c.setName(request.name().trim());
        c.setDescription(AuthService.blankToNull(request.description()));
        if (request.parentId() == null) {
            c.setParent(null);
            return;
        }
        Category parent = find(request.parentId());
        if (parent.isArchived()) throw new BadRequestException("The parent category is archived");
        // Prevent cycles: the new parent may not be this category or one of its descendants.
        for (Category a : ancestors(parent)) {
            if (c.getId() != null && a.getId().equals(c.getId())) {
                throw new BadRequestException("A category cannot be moved under itself or its subcategory");
            }
        }
        if (ancestors(parent).size() >= MAX_DEPTH) {
            throw new BadRequestException("Categories can be nested at most " + MAX_DEPTH + " levels deep");
        }
        c.setParent(parent);
    }
}
