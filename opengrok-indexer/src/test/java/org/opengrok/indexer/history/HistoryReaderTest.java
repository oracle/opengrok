package org.opengrok.indexer.history;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class HistoryReaderTest {
	@Test
	void testNullCbuf() throws Exception {
		assertThrows(NullPointerException.class, () -> {
			HistoryReader historyReader = new HistoryReader(new History());
			historyReader.read(null, 0, 0);
		});
	}
}