package com.example.logistics.shared.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses a deliberately small, allowlisted query-filter grammar; modules own value typing and predicate creation. */
public final class FilterParser {
    private static final Pattern EXPRESSION = Pattern.compile("^([A-Za-z][A-Za-z0-9]*)(?:\\[(eq|ne|gt|gte|lt|lte|contains|in)])?$");
    private FilterParser() {}

    public static List<FilterCriterion> parse(Map<String, List<String>> query, Set<String> allowedFields) {
        List<FilterCriterion> result = new ArrayList<>();
        query.forEach((expression, rawValues) -> {
            if (result.size() >= 20) throw new IllegalArgumentException("at most 20 filters are allowed");
            Matcher matcher = EXPRESSION.matcher(expression);
            if (!matcher.matches() || !allowedFields.contains(matcher.group(1)))
                throw new IllegalArgumentException("filter field or operator is not allowed");
            var operator = parseOperator(matcher.group(2));
            if (rawValues == null || rawValues.isEmpty()) throw new IllegalArgumentException("filter value is required");
            List<String> values = operator == FilterCriterion.Operator.IN
                    ? List.of(rawValues.getFirst().split(",", -1)) : List.of(rawValues.getFirst());
            if (values.isEmpty() || values.stream().anyMatch(v -> v.isBlank() || v.length() > 200))
                throw new IllegalArgumentException("filter value is invalid");
            result.add(new FilterCriterion(matcher.group(1), operator, values));
        });
        return List.copyOf(result);
    }

    private static FilterCriterion.Operator parseOperator(String text) {
        if (text == null) return FilterCriterion.Operator.EQ;
        return switch (text) {
            case "eq" -> FilterCriterion.Operator.EQ;
            case "ne" -> FilterCriterion.Operator.NE;
            case "gt" -> FilterCriterion.Operator.GT;
            case "gte" -> FilterCriterion.Operator.GTE;
            case "lt" -> FilterCriterion.Operator.LT;
            case "lte" -> FilterCriterion.Operator.LTE;
            case "contains" -> FilterCriterion.Operator.CONTAINS;
            case "in" -> FilterCriterion.Operator.IN;
            default -> throw new IllegalArgumentException("filter operator is not allowed");
        };
    }
}
