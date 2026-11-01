package io.github.dmytrostasyuk.packagetreesuite;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NaturalOrderTest {

    @Test
    void ignoresCaseComparesNumbersByValueAndPutsUnderscoreBeforeLetters() {
        List<String> names = new ArrayList<>(Arrays.asList(
                "steps", "Item10", "beta", "step_one", "Item2", "Alpha", "item1", "Item02x", "sub"));
        names.sort(NaturalOrder.INSTANCE);
        assertEquals(Arrays.asList(
                "Alpha", "beta", "item1", "Item2", "Item02x", "Item10", "step_one", "steps", "sub"), names);
    }
}
