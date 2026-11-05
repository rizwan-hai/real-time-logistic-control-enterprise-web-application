package com.example.logistics.shared.api;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestFactoryTest {
    private static final Set<String> ALLOWED = Set.of("createdAt", "name");
    private static final Set<String> FILTER_FIELDS = Set.of("status", "customerId");

    @Test void createsStableBoundedPageable() throws Exception {
        Object pageable = invoke(pageFactory(), 2, 25, "name,asc", ALLOWED);
        assertThat(pageable.getClass().getMethod("getPageNumber").invoke(pageable)).isEqualTo(2);
        assertThat(pageable.getClass().getMethod("getPageSize").invoke(pageable)).isEqualTo(25);
        Object sort = pageable.getClass().getMethod("getSort").invoke(pageable);
        Object order = sort.getClass().getMethod("getOrderFor", String.class).invoke(sort, "name");
        assertThat(order.getClass().getMethod("isAscending").invoke(order)).isEqualTo(true);
    }

    @Test void rejectsUnknownSortFieldsAndExcessivePageSize() throws Exception {
        assertThatThrownBy(() -> invoke(pageFactory(), 0, 10, "password,asc", ALLOWED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> invoke(pageFactory(), 0, 101, null, ALLOWED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void parsesAllowlistedFiltersAndOperators() throws Exception {
        Object filters = invoke(filterParser(), Map.of("status[in]", List.of("READY,IN_TRANSIT"), "customerId", List.of("abc")), FILTER_FIELDS);
        assertThat(filters.toString()).contains("READY, IN_TRANSIT", "customerId");
    }

    @Test void rejectsUnrecognizedFilterFields() throws Exception {
        assertThatThrownBy(() -> invoke(filterParser(), Map.of("sql;drop[in]", List.of("x")), FILTER_FIELDS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Method pageFactory() throws Exception {
        return Class.forName("com.example.logistics.shared.api.PageRequestFactory")
                .getMethod("create", int.class, int.class, String.class, Set.class);
    }
    private Method filterParser() throws Exception {
        return Class.forName("com.example.logistics.shared.api.FilterParser")
                .getMethod("parse", Map.class, Set.class);
    }
    private Object invoke(Method method, Object... args) throws Exception {
        try { return method.invoke(null, args); }
        catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) throw runtime;
            throw ex;
        }
    }
}
