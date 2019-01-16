package org.opengrok.indexer.util;

import org.apache.tools.ant.filters.StringInputStream;
import org.junit.Assert;
import org.junit.Test;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.web.Statistics;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

import static org.opengrok.indexer.util.StatisticsUtils.loadStatistics;
import static org.opengrok.indexer.util.StatisticsUtils.saveStatistics;

public class StatisticsUtilsTest {
    /**
     * Creates a map of String key and Long values.
     *
     * @param input double array containing the pairs
     * @return the map
     */
    private Map<String, Long> createMap(Object[][] input) {
        Map<String, Long> map = new TreeMap<>();
        for (int i = 0; i < input.length; i++) {
            map.put((String) input[i][0], (Long) input[i][1]);
        }
        return map;
    }

    @Test
    public void testLoadEmptyStatistics() throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String json = "{}";
        try (InputStream in = new StringInputStream(json)) {
            loadStatistics(in);
        }
        Assert.assertEquals(new org.opengrok.indexer.web.Statistics().toJson(), env.getStatistics().toJson());
    }

    @Test
    public void testLoadStatistics() throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String json = "{"
                + "\"requests_per_minute_max\":3,"
                + "\"timing\":{"
                + "\"*\":2288,"
                + "\"xref\":53,"
                + "\"root\":2235"
                + "},"
                + "\"minutes\":756,"
                + "\"timing_min\":{"
                + "\"*\":2,"
                + "\"xref\":2,"
                + "\"root\":2235"
                + "},"
                + "\"timing_avg\":{"
                + "\"*\":572.0,"
                + "\"xref\":17.666666666666668,"
                + "\"root\":2235.0"
                + "},"
                + "\"request_categories\":{"
                + "\"*\":4,"
                + "\"xref\":3,"
                + "\"root\":1"
                + "},"
                + "\"day_histogram\":[0,0,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,1],"
                + "\"requests\":4,"
                + "\"requests_per_minute_min\":1,"
                + "\"requests_per_minute\":3,"
                + "\"requests_per_minute_avg\":0.005291005291005291,"
                + "\"month_histogram\":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,3,0],"
                + "\"timing_max\":{"
                + "\"*\":2235,"
                + "\"xref\":48,"
                + "\"root\":2235"
                + "}"
                + "}";
        try (InputStream in = new StringInputStream(json)) {
            loadStatistics(in);
        }
        org.opengrok.indexer.web.Statistics stats = env.getStatistics();
        Assert.assertNotNull(stats);
        Assert.assertEquals(756, stats.getMinutes());
        Assert.assertEquals(4, stats.getRequests());
        Assert.assertEquals(3, stats.getRequestsPerMinute());
        Assert.assertEquals(1, stats.getRequestsPerMinuteMin());
        Assert.assertEquals(3, stats.getRequestsPerMinuteMax());
        Assert.assertEquals(0.005291005291005291, stats.getRequestsPerMinuteAvg(), 0.00005);

        Assert.assertArrayEquals(new long[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 3, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 1}, stats.getDayHistogram());
        Assert.assertArrayEquals(new long[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 1, 3,
                0}, stats.getMonthHistogram());

        Assert.assertEquals(createMap(new Object[][]{{"*", 4L}, {"xref", 3L}, {"root", 1L}}), stats.getRequestCategories());

        Assert.assertEquals(createMap(new Object[][]{{"*", 2288L}, {"xref", 53L}, {"root", 2235L}}), stats.getTiming());
        Assert.assertEquals(createMap(new Object[][]{{"*", 2L}, {"xref", 2L}, {"root", 2235L}}), stats.getTimingMin());
        Assert.assertEquals(createMap(new Object[][]{{"*", 2235L}, {"xref", 48L}, {"root", 2235L}}), stats.getTimingMax());
    }

    @Test(expected = IOException.class)
    public void testLoadInvalidStatistics() throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        String json = "{ malformed json with missing bracket";
        try (InputStream in = new StringInputStream(json)) {
            loadStatistics(in);
        }
    }

    @Test
    public void testSaveStatistics() throws IOException {
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        env.setStatistics(new Statistics());
        env.getStatistics().addRequest();
        env.getStatistics().addRequest("root");
        env.getStatistics().addRequestTime("root", 10L);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            saveStatistics(out);
            Assert.assertNotEquals("{}", out.toString());
            Assert.assertEquals(env.getStatistics().toJson(), out.toString());
        }
    }

    @Test(expected = IOException.class)
    public void testSaveNullStatistics() throws IOException {
        RuntimeEnvironment.getInstance().setStatisticsFilePath(null);
        saveStatistics();
    }

    @Test(expected = IOException.class)
    public void testSaveNullStatisticsFile() throws IOException {
        saveStatistics((File) null);
    }

    @Test(expected = IOException.class)
    public void testLoadNullStatistics() throws IOException {
        RuntimeEnvironment.getInstance().setStatisticsFilePath(null);
        loadStatistics();
    }

    @Test(expected = IOException.class)
    public void testLoadNullStatisticsFile() throws IOException {
        loadStatistics((File) null);
    }
}
