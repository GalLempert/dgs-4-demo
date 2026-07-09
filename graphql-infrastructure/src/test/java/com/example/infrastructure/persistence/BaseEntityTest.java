package com.example.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    static class TestEntity extends BaseEntity {
    }

    @Test
    void creationStampsBothTimestampsIdentically() {
        TestEntity entity = new TestEntity();

        entity.onCreate();

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
    }

    @Test
    void updateMovesOnlyTheUpdateTimestamp() throws InterruptedException {
        TestEntity entity = new TestEntity();
        entity.onCreate();
        LocalDateTime created = entity.getCreatedAt();

        Thread.sleep(5);
        entity.onUpdate();

        assertThat(entity.getCreatedAt()).isEqualTo(created);
        assertThat(entity.getUpdatedAt()).isAfter(created);
    }

    @Test
    void idIsUnassignedUntilPersisted() {
        assertThat(new TestEntity().getId()).isNull();
    }
}
