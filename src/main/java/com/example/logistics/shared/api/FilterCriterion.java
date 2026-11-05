package com.example.logistics.shared.api;

import java.util.List;

public record FilterCriterion(String field, Operator operator, List<String> values) {
    public enum Operator { EQ, NE, GT, GTE, LT, LTE, CONTAINS, IN }
}
