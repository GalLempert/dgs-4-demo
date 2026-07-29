package com.example.infrastructure.replication;

import com.example.infrastructure.error.ApiException;
import com.example.infrastructure.error.ErrorCode;
import com.example.infrastructure.filter.FilterCriteria;
import com.example.infrastructure.mapping.DeclarativeMapper;
import com.example.infrastructure.persistence.BaseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The write pipeline of {@link ResourceService} against a mocked DAL: what each of
 * the five standard mutations fetches, how the input lands on the entities, that the
 * hooks (validate, calculateDerivedFields, naturalKeyOf) run at the right points, and
 * that the two orchestrators delegate instead of re-implementing create/update.
 */
class ResourceServiceMutationTest {

    // ------------------------------------------------------------- fixtures

    public static class Widget extends BaseEntity {
        String name;
        Integer score;
        String derived;
    }

    public static class WidgetInput {
        public String name;
        public Integer score;
    }

    public static class WidgetView {
        public String name;
        public Integer score;
    }

    private static class WidgetService extends ResourceService<Widget, WidgetView> {

        final List<String> pipelineCalls = new ArrayList<>();
        boolean rejectValidation;

        WidgetService(ResourceDal<Widget> dal, DeclarativeMapper mapper) {
            super(dal, mapper, Widget.class);
        }

        @Override
        protected WidgetView toView(Widget entity) {
            return declarativeMapper().map(entity, WidgetView.class);
        }

        @Override
        protected void validate(Widget entity) {
            pipelineCalls.add("validate:" + entity.name);
            if (rejectValidation) {
                throw new ApiException(ErrorCode.INVALID_ARGUMENT, "rejected by domain rule",
                        Collections.emptyList());
            }
        }

        @Override
        protected void calculateDerivedFields(Widget entity) {
            pipelineCalls.add("calculate:" + entity.name);
            entity.derived = "derived-of-" + entity.name;
        }

        @Override
        protected FilterCriteria naturalKeyOf(Object input) {
            return FilterCriteria.whereEquals("name", ((WidgetInput) input).name);
        }
    }

    @SuppressWarnings("unchecked")
    private final ResourceDal<Widget> dal = mock(ResourceDal.class);
    private final WidgetService service =
            new WidgetService(dal, new DeclarativeMapper(new ObjectMapper().findAndRegisterModules()));

    private static WidgetInput input(String name, Integer score) {
        WidgetInput input = new WidgetInput();
        input.name = name;
        input.score = score;
        return input;
    }

    private static Widget widget(String name, Integer score) {
        Widget widget = new Widget();
        widget.name = name;
        widget.score = score;
        return widget;
    }

    private void stubPassThroughSaves() {
        when(dal.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(dal.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static final FilterCriteria SOME_FILTER = FilterCriteria.whereEquals("name", "x");

    // ---------------------------------------------------------------- saveNew

    @Test
    void saveNewMapsValidatesCalculatesAndSaves() {
        stubPassThroughSaves();

        WidgetView view = service.saveNew(input("fresh", 3));

        assertThat(view.name).isEqualTo("fresh");
        assertThat(service.pipelineCalls).containsExactly("validate:fresh", "calculate:fresh");
        verify(dal).save(any(Widget.class));
    }

    @Test
    void saveNewPropagatesValidationFailuresWithoutSaving() {
        service.rejectValidation = true;

        assertThatThrownBy(() -> service.saveNew(input("bad", 1)))
                .isInstanceOf(ApiException.class);
        verify(dal, never()).save(any());
    }

    // ----------------------------------------------------------------- update

    @Test
    void updateMergesInputOntoEveryMatchAndBulkSaves() {
        Widget first = widget("first", 1);
        Widget second = widget("second", 2);
        when(dal.findAll(SOME_FILTER)).thenReturn(Arrays.asList(first, second));
        stubPassThroughSaves();

        List<WidgetView> views = service.update(SOME_FILTER, input(null, 42));

        // non-null input fields land on every matching entity; null fields don't
        assertThat(first.score).isEqualTo(42);
        assertThat(second.score).isEqualTo(42);
        assertThat(first.name).isEqualTo("first");
        assertThat(second.name).isEqualTo("second");
        // pipeline ran per row, then ONE bulk save
        assertThat(service.pipelineCalls).containsExactly(
                "validate:first", "calculate:first", "validate:second", "calculate:second");
        verify(dal).saveAll(Arrays.asList(first, second));
        assertThat(views).hasSize(2);
    }

    @Test
    void updateRejectsAnEmptyFilter() {
        assertThatThrownBy(() -> service.update(FilterCriteria.none(), input("x", 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("non-empty filter");
        verify(dal, never()).findAll(any());
    }

    // ----------------------------------------------------------- saveOrUpdate

    @Test
    void saveOrUpdateCreatesWhenNothingMatches() {
        when(dal.count(SOME_FILTER, false)).thenReturn(0L);
        stubPassThroughSaves();

        List<WidgetView> views = service.saveOrUpdate(SOME_FILTER, input("created", 1), input(null, 9));

        // create path: the primary input is used, the update input ignored
        assertThat(views).hasSize(1);
        assertThat(views.get(0).name).isEqualTo("created");
        verify(dal).save(any(Widget.class));
        verify(dal, never()).saveAll(anyList());
    }

    @Test
    void saveOrUpdateUpdatesMatchesWithTheUpdateInput() {
        Widget existing = widget("existing", 1);
        when(dal.count(SOME_FILTER, false)).thenReturn(1L);
        when(dal.findAll(SOME_FILTER)).thenReturn(Collections.singletonList(existing));
        stubPassThroughSaves();

        service.saveOrUpdate(SOME_FILTER, input("created", 1), input(null, 9));

        // update path: the updateInput is applied, not the create input
        assertThat(existing.name).isEqualTo("existing");
        assertThat(existing.score).isEqualTo(9);
        verify(dal, never()).save(any());
        verify(dal).saveAll(anyList());
    }

    @Test
    void saveOrUpdateFallsBackToTheCreateInputWhenNoUpdateInputGiven() {
        Widget existing = widget("existing", 1);
        when(dal.count(SOME_FILTER, false)).thenReturn(1L);
        when(dal.findAll(SOME_FILTER)).thenReturn(Collections.singletonList(existing));
        stubPassThroughSaves();

        service.saveOrUpdate(SOME_FILTER, input("renamed", 5), null);

        assertThat(existing.name).isEqualTo("renamed");
        assertThat(existing.score).isEqualTo(5);
    }

    // --------------------------------------------------------- saveOrOverride

    @Test
    void saveOrOverrideCreatesWhenTheNaturalKeyIsUnknown() {
        when(dal.findAll(any())).thenReturn(Collections.emptyList());
        stubPassThroughSaves();

        WidgetView view = service.saveOrOverride(input("novel", 2));

        assertThat(view.name).isEqualTo("novel");
        verify(dal).save(any(Widget.class));
    }

    @Test
    void saveOrOverrideReplacesTheWholeBusinessStateOfTheExistingRow() {
        Widget existing = widget("known", 7);
        existing.derived = "stale";
        when(dal.findAll(any())).thenReturn(Collections.singletonList(existing));
        stubPassThroughSaves();

        // override input carries name but a null score - override semantics null it out
        service.saveOrOverride(input("known", null));

        assertThat(existing.name).isEqualTo("known");
        assertThat(existing.score).isNull();
        assertThat(existing.derived).isEqualTo("derived-of-known");
        verify(dal).save(existing);
    }

    @Test
    void saveOrOverrideFailsLoudlyWhenTheNaturalKeyMatchesSeveralRows() {
        when(dal.findAll(any())).thenReturn(Arrays.asList(widget("dup", 1), widget("dup", 2)));

        assertThatThrownBy(() -> service.saveOrOverride(input("dup", 3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("natural key");
        verify(dal, never()).save(any());
    }

    @Test
    void saveOrOverrideWithoutANaturalKeyHookIsAWiringError() {
        ResourceService<Widget, WidgetView> hookless =
                new ResourceService<Widget, WidgetView>(dal, new DeclarativeMapper(new ObjectMapper().findAndRegisterModules()),
                        Widget.class) {
                    @Override
                    protected WidgetView toView(Widget entity) {
                        return declarativeMapper().map(entity, WidgetView.class);
                    }
                };

        assertThatThrownBy(() -> hookless.saveOrOverride(input("any", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("naturalKeyOf");
    }

    // ---------------------------------------------------------- deleteByFilter

    @Test
    void deleteByFilterSoftDeletesEveryMatchInOneBulkSave() {
        Widget first = widget("doomed-a", 1);
        Widget second = widget("doomed-b", 2);
        when(dal.findAll(SOME_FILTER)).thenReturn(Arrays.asList(first, second));
        stubPassThroughSaves();

        int deleted = service.deleteByFilter(SOME_FILTER);

        assertThat(deleted).isEqualTo(2);
        assertThat(first.isDeleted()).isTrue();
        assertThat(second.isDeleted()).isTrue();
        verify(dal).saveAll(Arrays.asList(first, second));
    }

    @Test
    void deleteByFilterRejectsAnEmptyFilter() {
        assertThatThrownBy(() -> service.deleteByFilter(FilterCriteria.none()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("non-empty filter");
        verify(dal, never()).findAll(any());
    }
}
