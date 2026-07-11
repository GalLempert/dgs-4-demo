package com.example.infrastructure.mapping;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeclarativeMapperTest {

    private final DeclarativeMapper declarativeMapper = new DeclarativeMapper(new ObjectMapper());

    // ------------------------------------------------------------- fixtures

    /** Source side: a mutable input DTO with public fields. */
    public static class OrderInput {
        public String reference;
        public Integer quantity;              // null in some tests
        public String unknownToTarget;        // no counterpart on OrderEntity
        public List<LineInput> lines;
    }

    public static class LineInput {
        public String sku;
    }

    /** Target side: entity-style class - private fields, no setters, defaults. */
    static class OrderEntity {
        private String reference;
        private int quantity = 5;             // default that must survive null input

        @JsonManagedReference
        private List<LineEntity> lines;

        String getReference() {
            return reference;
        }

        int getQuantity() {
            return quantity;
        }

        List<LineEntity> getLines() {
            return lines;
        }
    }

    static class LineEntity {
        private String sku;

        @JsonBackReference
        private OrderEntity order;

        String getSku() {
            return sku;
        }

        OrderEntity getOrder() {
            return order;
        }
    }

    // ----------------------------------------------------------------- tests

    @Test
    void mapsMatchingFieldsWithoutSettersOnTheTarget() {
        OrderInput input = new OrderInput();
        input.reference = "ORD-1";
        input.quantity = 3;

        OrderEntity entity = declarativeMapper.map(input, OrderEntity.class);

        assertThat(entity.getReference()).isEqualTo("ORD-1");
        assertThat(entity.getQuantity()).isEqualTo(3);
    }

    @Test
    void nullInputFieldsPreserveTargetDefaults() {
        OrderInput input = new OrderInput();
        input.reference = "ORD-2";
        input.quantity = null;

        OrderEntity entity = declarativeMapper.map(input, OrderEntity.class);

        assertThat(entity.getQuantity()).isEqualTo(5);
    }

    @Test
    void unknownSourceFieldsAreIgnored() {
        OrderInput input = new OrderInput();
        input.reference = "ORD-3";
        input.unknownToTarget = "does not exist on the entity";

        OrderEntity entity = declarativeMapper.map(input, OrderEntity.class);

        assertThat(entity.getReference()).isEqualTo("ORD-3");
    }

    @Test
    void managedAndBackReferencesWireParentChildRelationships() {
        LineInput first = new LineInput();
        first.sku = "SKU-A";
        LineInput second = new LineInput();
        second.sku = "SKU-B";
        OrderInput input = new OrderInput();
        input.reference = "ORD-4";
        input.lines = Arrays.asList(first, second);

        OrderEntity entity = declarativeMapper.map(input, OrderEntity.class);

        assertThat(entity.getLines()).hasSize(2);
        assertThat(entity.getLines().get(0).getSku()).isEqualTo("SKU-A");
        // the declarative wiring: every child points back at the mapped parent
        assertThat(entity.getLines()).allSatisfy(line ->
                assertThat(line.getOrder()).isSameAs(entity));
    }

    /** View-style target: private fields, getters only - the entity->view direction. */
    static class OrderView {
        private String reference;
        private int quantity;
        private List<LineView> lines;

        String getReference() {
            return reference;
        }

        int getQuantity() {
            return quantity;
        }

        List<LineView> getLines() {
            return lines;
        }
    }

    static class LineView {
        private String sku;

        String getSku() {
            return sku;
        }
    }

    @Test
    void mapsEntitiesToViewsIncludingNestedCollections() {
        LineInput line = new LineInput();
        line.sku = "SKU-C";
        OrderInput input = new OrderInput();
        input.reference = "ORD-5";
        input.quantity = 2;
        input.lines = Collections.singletonList(line);
        OrderEntity entity = declarativeMapper.map(input, OrderEntity.class);

        OrderView view = declarativeMapper.map(entity, OrderView.class);

        assertThat(view.getReference()).isEqualTo("ORD-5");
        assertThat(view.getQuantity()).isEqualTo(2);
        // children map by name; the @JsonBackReference parent link is not serialized,
        // so entity->view conversion cannot recurse
        assertThat(view.getLines()).hasSize(1);
        assertThat(view.getLines().get(0).getSku()).isEqualTo("SKU-C");
    }

    @Test
    void doesNotMutateTheSharedApplicationObjectMapper() {
        ObjectMapper application = new ObjectMapper();
        int settingsBefore = application.getSerializationConfig().getSerializationInclusion().hashCode();

        new DeclarativeMapper(application);

        assertThat(application.getSerializationConfig().getSerializationInclusion().hashCode())
                .isEqualTo(settingsBefore);
    }
}
