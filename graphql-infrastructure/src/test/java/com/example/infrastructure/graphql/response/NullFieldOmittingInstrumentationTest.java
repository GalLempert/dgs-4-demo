package com.example.infrastructure.graphql.response;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphqlErrorBuilder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NullFieldOmittingInstrumentationTest {

    private final NullFieldOmittingInstrumentation enabled = new NullFieldOmittingInstrumentation(true);
    private final NullFieldOmittingInstrumentation disabled = new NullFieldOmittingInstrumentation(false);

    @Test
    void disabledToggleLeavesResultUntouched() {
        ExecutionResult result = resultWithData(map("firstName", "Ada", "nickname", null));

        ExecutionResult instrumented = disabled.instrumentExecutionResult(result, null).join();

        assertThat(instrumented).isSameAs(result);
        assertThat((Map<String, Object>) instrumented.getData()).containsEntry("nickname", null);
    }

    @Test
    void enabledToggleDropsNullFieldsRecursively() {
        Map<String, Object> person = map(
                "firstName", "Ada",
                "nickname", null,
                "address", map("city", "Tel Aviv", "zipCode", null));
        ExecutionResult result = resultWithData(map("personById", person));

        Map<String, Object> data = enabled.instrumentExecutionResult(result, null).join().getData();

        Map<String, Object> prunedPerson = (Map<String, Object>) data.get("personById");
        assertThat(prunedPerson).containsOnlyKeys("firstName", "address");
        assertThat((Map<String, Object>) prunedPerson.get("address")).containsOnlyKeys("city");
    }

    @Test
    void enabledTogglePrunesObjectsInsideListsButKeepsNullListElements() {
        List<Object> persons = Arrays.asList(map("nickname", null, "age", 30), null);
        ExecutionResult result = resultWithData(map("allPersons", persons));

        Map<String, Object> data = enabled.instrumentExecutionResult(result, null).join().getData();

        List<Object> prunedList = (List<Object>) data.get("allPersons");
        assertThat(prunedList).hasSize(2);
        assertThat((Map<String, Object>) prunedList.get(0)).containsOnlyKeys("age");
        assertThat(prunedList.get(1)).isNull();
    }

    @Test
    void errorsAndExtensionsSurvivePruning() {
        Map<Object, Object> extensions = new LinkedHashMap<>();
        extensions.put("traceId", "abc");
        ExecutionResult result = ExecutionResultImpl.newExecutionResult()
                .data(map("personById", null))
                .addError(GraphqlErrorBuilder.newError().message("boom").build())
                .extensions(extensions)
                .build();

        ExecutionResult instrumented = enabled.instrumentExecutionResult(result, null).join();

        assertThat((Map<String, Object>) instrumented.getData()).isEmpty();
        assertThat(instrumented.getErrors()).extracting(error -> error.getMessage()).containsExactly("boom");
        assertThat(instrumented.getExtensions()).containsEntry("traceId", "abc");
    }

    @Test
    void resultWithoutDataPassesThrough() {
        ExecutionResult result = ExecutionResultImpl.newExecutionResult()
                .addError(GraphqlErrorBuilder.newError().message("boom").build())
                .build();

        assertThat(enabled.instrumentExecutionResult(result, null).join()).isSameAs(result);
    }

    private static ExecutionResult resultWithData(Object data) {
        return ExecutionResultImpl.newExecutionResult().data(data).build();
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
