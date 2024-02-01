package org.opengrok.indexer.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class OpenGrokThreadFactoryTest {
    @Test
    void testNullRunnable() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            OpenGrokThreadFactory factory = new OpenGrokThreadFactory("");
            factory.newThread(null);
        });
    }
}