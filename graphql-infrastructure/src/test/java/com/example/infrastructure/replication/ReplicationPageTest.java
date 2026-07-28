package com.example.infrastructure.replication;

import com.example.infrastructure.persistence.BaseEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReplicationPageTest {

    static class TestEntity extends BaseEntity {
        final String name;

        TestEntity(long id, String name, long sequence, boolean deleted) {
            this.name = name;
            ReflectionTestUtils.setField(this, "id", id);
            setSequence(sequence);
            setDeleted(deleted);
        }
    }

    private final TestEntity liveMatching = new TestEntity(1, "live-matching", 10, false);
    private final TestEntity deletedMatching = new TestEntity(2, "deleted-matching", 11, true);
    private final TestEntity liveFilteredOut = new TestEntity(3, "live-filtered-out", 12, false);
    private final TestEntity deletedFilteredOut = new TestEntity(4, "deleted-filtered-out", 13, true);

    @Test
    void partitionsMatchingRowsByDeletedFlagAndReportsTheRestById() {
        List<TestEntity> batch = Arrays.asList(liveMatching, deletedMatching, liveFilteredOut, deletedFilteredOut);
        Set<Long> matchingIds = new HashSet<>(Arrays.asList(1L, 2L));

        ReplicationPage<String> page = ReplicationPage.partition(batch, matchingIds, 13, e -> e.name);

        assertThat(page.getUpdated()).containsExactly("live-matching");
        assertThat(page.getDeleted()).containsExactly("deleted-matching");
        assertThat(page.getFilteredOutIds()).containsExactly(3L, 4L);
        assertThat(page.getNextSequence()).isEqualTo(13);
    }

    @Test
    void everythingMatchesWhenAllIdsAreMatching() {
        List<TestEntity> batch = Arrays.asList(liveMatching, deletedMatching);
        Set<Long> matchingIds = new HashSet<>(Arrays.asList(1L, 2L));

        ReplicationPage<String> page = ReplicationPage.partition(batch, matchingIds, 11, e -> e.name);

        assertThat(page.getUpdated()).containsExactly("live-matching");
        assertThat(page.getDeleted()).containsExactly("deleted-matching");
        assertThat(page.getFilteredOutIds()).isEmpty();
    }

    @Test
    void emptyBatchYieldsEmptyPageWithTheGivenResumePoint() {
        ReplicationPage<String> page =
                ReplicationPage.partition(Collections.emptyList(), Collections.emptySet(), 42, e -> "unused");

        assertThat(page.getUpdated()).isEmpty();
        assertThat(page.getDeleted()).isEmpty();
        assertThat(page.getFilteredOutIds()).isEmpty();
        assertThat(page.getNextSequence()).isEqualTo(42);
    }
}
