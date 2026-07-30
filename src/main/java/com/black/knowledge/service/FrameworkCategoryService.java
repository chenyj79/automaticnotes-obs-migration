package com.black.knowledge.service;

import cn.hutool.core.util.StrUtil;
import com.black.knowledge.dto.FrameworkCategoryRequest;
import com.black.knowledge.po.FrameworkCategory;
import com.black.knowledge.repository.FrameworkCategoryRepository;
import com.black.knowledge.vo.FrameworkCategorySuggestionVo;
import com.black.knowledge.vo.FrameworkCategoryVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FrameworkCategoryService {

    public static final String DEFAULT_CATEGORY_NAME = "其他";
    public static final String DEFAULT_CATEGORY_DEFINITION = "所有的内容都划分到这里";

    private final FrameworkCategoryRepository frameworkCategoryRepository;
    private final AiService aiService;

    @Transactional
    public List<FrameworkCategoryVo> saveFrameworkCategories(Long frameworkId, List<FrameworkCategoryRequest> requests) {
        List<FrameworkCategoryRequest> normalized = normalizeRequests(requests);
        frameworkCategoryRepository.deleteByFrameworkId(frameworkId);

        List<FrameworkCategory> entities = new ArrayList<>();
        for (int i = 0; i < normalized.size(); i++) {
            FrameworkCategoryRequest request = normalized.get(i);
            FrameworkCategory category = new FrameworkCategory();
            category.setFrameworkId(frameworkId);
            category.setName(request.getName().trim());
            category.setDefinition(request.getDefinition().trim());
            category.setSortOrder(i);
            entities.add(category);
        }
        return frameworkCategoryRepository.saveAll(entities).stream().map(FrameworkCategory::toVo).toList();
    }

    @Transactional
    public List<FrameworkCategoryVo> ensureDefaultCategory(Long frameworkId) {
        List<FrameworkCategory> categories = frameworkCategoryRepository.findByFrameworkIdOrderBySortOrderAscIdAsc(frameworkId);
        if (!categories.isEmpty()) {
            return categories.stream().map(FrameworkCategory::toVo).toList();
        }

        FrameworkCategory fallback = new FrameworkCategory();
        fallback.setFrameworkId(frameworkId);
        fallback.setName(DEFAULT_CATEGORY_NAME);
        fallback.setDefinition(DEFAULT_CATEGORY_DEFINITION);
        fallback.setSortOrder(0);
        FrameworkCategory saved = frameworkCategoryRepository.save(fallback);
        return List.of(saved.toVo());
    }

    public List<FrameworkCategoryVo> getFrameworkCategories(Long frameworkId) {
        List<FrameworkCategory> categories = frameworkCategoryRepository.findByFrameworkIdOrderBySortOrderAscIdAsc(frameworkId);
        if (categories.isEmpty()) {
            return List.of(defaultCategoryVo());
        }
        return categories.stream().map(FrameworkCategory::toVo).toList();
    }

    public List<FrameworkCategory> getFrameworkCategoryEntities(Long frameworkId) {
        return frameworkCategoryRepository.findByFrameworkIdOrderBySortOrderAscIdAsc(frameworkId);
    }

    public List<String> getFrameworkCategoryNames(Long frameworkId) {
        List<FrameworkCategory> categories = frameworkCategoryRepository.findByFrameworkIdOrderBySortOrderAscIdAsc(frameworkId);
        if (categories.isEmpty()) {
            return List.of(DEFAULT_CATEGORY_NAME);
        }
        return categories.stream().map(FrameworkCategory::getName).toList();
    }

    public List<FrameworkCategorySuggestionVo> suggestCategories(String name, String subject, String description) {
        List<FrameworkCategorySuggestionVo> suggestions = aiService.suggestFrameworkCategories(name, subject, description);
        return normalizeSuggestions(suggestions);
    }

    public String buildCategoryDefinitionsForPrompt(Long frameworkId) {
        List<FrameworkCategory> categories = getFrameworkCategoryEntities(frameworkId);
        if (categories.isEmpty()) {
            categories = List.of(buildDefaultCategory(frameworkId));
        }

        StringBuilder sb = new StringBuilder();
        for (FrameworkCategory category : categories) {
            sb.append("- ")
                    .append(category.getName())
                    .append(": ")
                    .append(category.getDefinition())
                    .append("\n");
        }
        return sb.toString();
    }

    public String resolveCategoryForFramework(Long frameworkId, String category) {
        List<String> names = getFrameworkCategoryNames(frameworkId);
        String fallback = names.contains(DEFAULT_CATEGORY_NAME) ? DEFAULT_CATEGORY_NAME : names.get(0);
        if (StrUtil.isBlank(category)) {
            return fallback;
        }
        String normalized = category.trim();
        Set<String> allowed = Set.copyOf(names);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    @Transactional
    public void deleteByFrameworkId(Long frameworkId) {
        frameworkCategoryRepository.deleteByFrameworkId(frameworkId);
    }

    private List<FrameworkCategorySuggestionVo> normalizeSuggestions(List<FrameworkCategorySuggestionVo> suggestions) {
        List<FrameworkCategoryRequest> requests = new ArrayList<>();
        if (suggestions != null) {
            for (FrameworkCategorySuggestionVo item : suggestions) {
                if (item == null) {
                    continue;
                }
                FrameworkCategoryRequest request = new FrameworkCategoryRequest();
                request.setName(item.getName());
                request.setDefinition(item.getDefinition());
                requests.add(request);
            }
        }

        return normalizeRequests(requests).stream().map(request -> {
            FrameworkCategorySuggestionVo vo = new FrameworkCategorySuggestionVo();
            vo.setName(request.getName());
            vo.setDefinition(request.getDefinition());
            return vo;
        }).toList();
    }

    private List<FrameworkCategoryRequest> normalizeRequests(List<FrameworkCategoryRequest> requests) {
        Map<String, FrameworkCategoryRequest> deduplicated = new LinkedHashMap<>();

        if (requests != null) {
            for (FrameworkCategoryRequest request : requests) {
                if (request == null || StrUtil.isBlank(request.getName()) || StrUtil.isBlank(request.getDefinition())) {
                    continue;
                }
                String name = request.getName().trim();
                if (name.length() > 64) {
                    name = name.substring(0, 64);
                }
                String key = name.toLowerCase();
                if (deduplicated.containsKey(key)) {
                    continue;
                }
                FrameworkCategoryRequest normalized = new FrameworkCategoryRequest();
                normalized.setName(name);
                normalized.setDefinition(request.getDefinition().trim());
                deduplicated.put(key, normalized);
                if (deduplicated.size() >= 12) {
                    break;
                }
            }
        }

        if (deduplicated.isEmpty()) {
            FrameworkCategoryRequest fallback = new FrameworkCategoryRequest();
            fallback.setName(DEFAULT_CATEGORY_NAME);
            fallback.setDefinition(DEFAULT_CATEGORY_DEFINITION);
            deduplicated.put(DEFAULT_CATEGORY_NAME, fallback);
        }

        return new ArrayList<>(deduplicated.values());
    }

    private FrameworkCategory buildDefaultCategory(Long frameworkId) {
        FrameworkCategory fallback = new FrameworkCategory();
        fallback.setFrameworkId(frameworkId);
        fallback.setName(DEFAULT_CATEGORY_NAME);
        fallback.setDefinition(DEFAULT_CATEGORY_DEFINITION);
        fallback.setSortOrder(0);
        return fallback;
    }

    private FrameworkCategoryVo defaultCategoryVo() {
        FrameworkCategoryVo vo = new FrameworkCategoryVo();
        vo.setName(DEFAULT_CATEGORY_NAME);
        vo.setDefinition(DEFAULT_CATEGORY_DEFINITION);
        vo.setSortOrder(0);
        return vo;
    }
}

