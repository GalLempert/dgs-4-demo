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
        String internalNote;                  // no counterpart on OrderInput

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

    // ------------------------------------------------- merge (partial update)

    @Test
    void mergeCopiesNonNullFieldsAndLeavesTheRestUntouched() {
        OrderEntity entity = new OrderEntity();
        entity.reference = "ORD-OLD";
        entity.quantity = 7;

        OrderInput input = new OrderInput();
        input.reference = "ORD-NEW";
        input.quantity = null;

        OrderEntity merged = declarativeMapper.merge(input, entity);

        assertThat(merged).isSameAs(entity);
        assertThat(entity.getReference()).isEqualTo("ORD-NEW");
        // null input field skipped - existing value survives
        assertThat(entity.getQuantity()).isEqualTo(7);
    }

    @Test
    void mergeRefillsExistingCollectionsInsteadOfReplacingThem() {
        LineInput oldLine = new LineInput();
        oldLine.sku = "SKU-OLD";
        OrderInput seed = new OrderInput();
        seed.reference = "ORD-6";
        seed.lines = Collections.singletonList(oldLine);
        OrderEntity entity = declarativeMapper.map(seed, OrderEntity.class);
        List<LineEntity> managedCollection = entity.getLines();

        LineInput newLine = new LineInput();
        newLine.sku = "SKU-NEW";
        OrderInput update = new OrderInput();
        update.lines = Collections.singletonList(newLine);

        declarativeMapper.merge(update, entity);

        // JPA-managed collections must keep their identity across a merge
        assertThat(entity.getLines()).isSameAs(managedCollection);
        assertThat(entity.getLines()).hasSize(1);
        assertThat(entity.getLines().get(0).getSku()).isEqualTo("SKU-NEW");
    }

    @Test
    void mergeWithoutCollectionFieldLeavesTheCollectionAlone() {
        LineInput line = new LineInput();
        line.sku = "SKU-KEEP";
        OrderInput seed = new OrderInput();
        seed.reference = "ORD-7";
        seed.lines = Collections.singletonList(line);
        OrderEntity entity = declarativeMapper.map(seed, OrderEntity.class);

        OrderInput update = new OrderInput();
        update.reference = "ORD-7B";

        declarativeMapper.merge(update, entity);

        assertThat(entity.getReference()).isEqualTo("ORD-7B");
        assertThat(entity.getLines()).hasSize(1);
        assertThat(entity.getLines().get(0).getSku()).isEqualTo("SKU-KEEP");
    }

    // ------------------------------------------------ override (full replace)

    @Test
    void overrideAppliesNullsSoTheTargetBecomesExactlyTheInput() {
        OrderEntity entity = new OrderEntity();
        entity.reference = "ORD-OLD";

        OrderInput input = new OrderInput();
        input.reference = null;
        input.quantity = 9;

        declarativeMapper.override(input, entity);

        // unlike merge, a null input field overwrites the existing value
        assertThat(entity.getReference()).isNull();
        assertThat(entity.getQuantity()).isEqualTo(9);
    }

    @Test
    void overrideClearsExistingCollectionsWhenTheInputCarriesNone() {
        LineInput line = new LineInput();
        line.sku = "SKU-GONE";
        OrderInput seed = new OrderInput();
        seed.reference = "ORD-8";
        seed.lines = Collections.singletonList(line);
        OrderEntity entity = declarativeMapper.map(seed, OrderEntity.class);
        List<LineEntity> managedCollection = entity.getLines();

        declarativeMapper.override(new OrderInput(), entity);

        assertThat(entity.getLines()).isSameAs(managedCollection);
        assertThat(entity.getLines()).isEmpty();
    }

    @Test
    void overrideDoesNotTouchFieldsTheSourceTypeDoesNotDeclare() {
        // entity-only field (no counterpart on OrderInput): stays intact through override
        OrderEntity entity = new OrderEntity();
        entity.reference = "ORD-9";
        entity.internalNote = "technical state";

        OrderInput input = new OrderInput();
        input.reference = "ORD-9B";

        declarativeMapper.override(input, entity);

        assertThat(entity.getReference()).isEqualTo("ORD-9B");
        assertThat(entity.internalNote).isEqualTo("technical state");
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
