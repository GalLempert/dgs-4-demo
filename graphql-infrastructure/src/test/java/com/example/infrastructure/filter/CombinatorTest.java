package com.example.infrastructure.filter;

import org.junit.jupiter.api.Test;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Each combinator value implements its own folding - enum as strategy. */
class CombinatorTest {

    private final CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
    private final Predicate[] predicates = {mock(Predicate.class), mock(Predicate.class)};
    private final Predicate combined = mock(Predicate.class);

    @Test
    void andDelegatesToCriteriaBuilderAnd() {
        when(criteriaBuilder.and(predicates)).thenReturn(combined);

        assertThat(FilterCriteria.Combinator.AND.combine(criteriaBuilder, predicates)).isSameAs(combined);
        verify(criteriaBuilder).and(predicates);
    }

    @Test
    void orDelegatesToCriteriaBuilderOr() {
        when(criteriaBuilder.or(predicates)).thenReturn(combined);

        assertThat(FilterCriteria.Combinator.OR.combine(criteriaBuilder, predicates)).isSameAs(combined);
        verify(criteriaBuilder).or(predicates);
    }
}
