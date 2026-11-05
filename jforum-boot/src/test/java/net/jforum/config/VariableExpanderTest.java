package net.jforum.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** JUnit 5 port of legacy tests/core/net/jforum/util/preferences/VariableExpanderTest. */
class VariableExpanderTest {

    private final Map<String, String> props = Map.of(
        "prop1", "value1",
        "prop2", "${prop1}/value2",
        "prop3", "${prop2}/value3",
        "cycle1", "${cycle2}",
        "cycle2", "${cycle1}");

    private final VariableExpander expander = new VariableExpander(props::get);

    @Test
    void simpleAndNestedExpansion() {
        assertEquals("value1", expander.expand("${prop1}"));
        assertEquals("value1/value2/value3", expander.expand("${prop3}"));
        assertEquals("a value1 b value1/value2 c", expander.expand("a ${prop1} b ${prop2} c"));
    }

    @Test
    void unknownAndUnterminatedVariablesAreKept() {
        assertEquals("${missing}", expander.expand("${missing}"));
        assertEquals("x ${prop1", expander.expand("x ${prop1"));
        assertEquals("no variables", expander.expand("no variables"));
    }

    @Test
    void cyclesDoNotLoop() {
        assertEquals("${cycle1}", expander.expand("${cycle1}"));
    }
}
