/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

 /*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.indexer.web;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Function;

/**
 *
 * @author Krystof Tulinger
 */
public class StatisticsTest {

    public StatisticsTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testNewInstance() {
        Statistics stat = new Statistics();
        Assert.assertNotNull(stat.getRequestCategories());
        Assert.assertNotNull(stat.getTiming());
        Assert.assertNotNull(stat.getTimingMin());
        Assert.assertNotNull(stat.getTimingMax());
        Assert.assertNotNull(stat.getTimingAvg());

        Assert.assertEquals(0, stat.getRequests());
        Assert.assertEquals(1, stat.getMinutes()); // current minute
        Assert.assertEquals(0, stat.getRequestsPerMinute());
        Assert.assertEquals(0, stat.getRequestsPerMinuteMin());
        Assert.assertEquals(0, stat.getRequestsPerMinuteMax());
        Assert.assertEquals(0, stat.getRequestsPerMinuteAvg(), 0.0);
    }

    @Test
    public void testMaybeRefresh() {
        long start = System.currentTimeMillis();

        long[] tests = new long[]{
            5 * 65 * 1000,
            7 * 63 * 1000,
            3256 * 60 * 1001,
            13647987987L * 60 * 1000
        };

        for (int i = 0; i < tests.length; i++) {
            Statistics stat = new Statistics();
            Assert.assertEquals(1, stat.getMinutes());
            stat.setTimeStart(start - tests[i]);
            Assert.assertEquals(stat.getTimeStart(), start - tests[i]);
            stat.maybeRefresh();
            Assert.assertEquals((tests[i] + 60 * 1000) / (60 * 1000), stat.getMinutes());
        }
    }

    @Test
    public void testAddRequestTimeCategory() {
        String[] testCategories = new String[]{
            "root", "/", "*", "!x.", "search", "xref"
        };
        for (String category : testCategories) {
            Statistics stat = new Statistics();
            stat.addRequestTime(category, (long) (Math.random() * Long.MAX_VALUE));
            Assert.assertEquals(1, stat.getRequestCategories().size());
            Assert.assertTrue(stat.getRequestCategories().containsKey(category));
            Assert.assertNotNull(stat.getRequestCategories().get(category));
            Assert.assertEquals(1, stat.getRequestCategories().get(category).longValue());
            Assert.assertEquals(1, stat.getTiming().size());
            Assert.assertEquals(1, stat.getTimingMin().size());
            Assert.assertEquals(1, stat.getTimingMax().size());
            Assert.assertEquals(1, stat.getTimingAvg().size());
            Assert.assertTrue(stat.getTiming().containsKey(category));
            Assert.assertTrue(stat.getTimingMin().containsKey(category));
            Assert.assertTrue(stat.getTimingMax().containsKey(category));
            Assert.assertTrue(stat.getTimingAvg().containsKey(category));
            Assert.assertNotNull(stat.getTiming().get(category));
            Assert.assertNotNull(stat.getTimingMin().get(category));
            Assert.assertNotNull(stat.getTimingMax().get(category));
            Assert.assertNotNull(stat.getTimingAvg().get(category));
        }
    }

    @Test
    public void testAddRequestTimeMultipleCategories() {
        Statistics stat = new Statistics();
        String[] testCategories = new String[]{
            "root", "/", "*", "/", "search", "/", "*", "/"
        };
        long[] testValues = new long[]{
            1, 1, 1, 2, 1, 3, 2, 4
        };

        int[] testSizes = new int[]{
            1, 2, 3, 3, 4, 4, 4, 4
        };

        Assert.assertEquals(testCategories.length, testValues.length);
        Assert.assertEquals(testCategories.length, testSizes.length);

        for (int i = 0; i < testCategories.length; i++) {
            stat.addRequestTime(testCategories[i], (long) (Math.random() * Long.MAX_VALUE));
            Assert.assertEquals(testSizes[i], stat.getRequestCategories().size());
            Assert.assertTrue(stat.getRequestCategories().containsKey(testCategories[i]));
            Assert.assertNotNull(stat.getRequestCategories().get(testCategories[i]));
            Assert.assertEquals(testValues[i], stat.getRequestCategories().get(testCategories[i]).longValue());
            Assert.assertEquals(testSizes[i], stat.getTiming().size());
            Assert.assertEquals(testSizes[i], stat.getTimingMin().size());
            Assert.assertEquals(testSizes[i], stat.getTimingMax().size());
            Assert.assertEquals(testSizes[i], stat.getTimingAvg().size());
            Assert.assertTrue(stat.getTiming().containsKey(testCategories[i]));
            Assert.assertTrue(stat.getTimingMin().containsKey(testCategories[i]));
            Assert.assertTrue(stat.getTimingMax().containsKey(testCategories[i]));
            Assert.assertTrue(stat.getTimingAvg().containsKey(testCategories[i]));
            Assert.assertNotNull(stat.getTiming().get(testCategories[i]));
            Assert.assertNotNull(stat.getTimingMin().get(testCategories[i]));
            Assert.assertNotNull(stat.getTimingMax().get(testCategories[i]));
            Assert.assertNotNull(stat.getTimingAvg().get(testCategories[i]));
        }
    }

    @Test
    public void testAddRequestTimeSum() {
        String[] testCategories = new String[]{
            "root", "/", "*", "!x.", "search", "xref"
        };
        long[] testValues = new long[]{
            10, 200, 300, -300, 100, 3641235546646L
        };

        Assert.assertEquals(testCategories.length, testValues.length);

        for (int i = 0; i < testCategories.length; i++) {
            Statistics stat = new Statistics();
            stat.addRequestTime(testCategories[i], testValues[i]);
            Assert.assertEquals(testValues[i], stat.getTiming().get(testCategories[i]).longValue());
        }
    }

    @Test
    public void testAddRequestMultipleTimeSum() {
        Statistics stat = new Statistics();
        String[] testCategories = new String[]{
            "root", "/", "root", "!x.", "/", "xref", "x"
        };
        long[] testValues = new long[]{
            10, 200, 300, -300, 100, 3641235546646L, 1
        };
        long[] testExpected = new long[]{
            10, 200, 310, -300, 300, 3641235546646L, 1
        };

        Assert.assertEquals(testCategories.length, testValues.length);
        Assert.assertEquals(testCategories.length, testExpected.length);

        for (int i = 0; i < testCategories.length; i++) {
            stat.addRequestTime(testCategories[i], testValues[i]);
            Assert.assertEquals(testExpected[i], stat.getTiming().get(testCategories[i]).longValue());
        }
    }

    /**
     * Test of toJson method, of class Statistics.
     */
    @Test
    public void testToJsonOnInstance() throws IOException {
        checkToJson(new Function<Statistics, String>() {
            @Override
            public String apply(Statistics t) {
                try {
                    return t.toJson();
                } catch (JsonProcessingException e) {
                    Assert.fail(e.toString());
                }
                return null;
            }
        });
    }

    /**
     * Test of fromJson method, of class Statistics.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testFrom() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        JsonFactory jfactory = new JsonFactory();
        JsonGenerator jGenerator = jfactory
                .createGenerator(stream, JsonEncoding.UTF8);

        jGenerator.writeStartObject();
        jGenerator.writeNumberField(Statistics.STATISTIC_MINUTES, 10L);
        jGenerator.writeNumberField(Statistics.STATISTIC_REQUESTS, 100L);
        jGenerator.writeNumberField(Statistics.STATISTIC_REQUESTS_PER_MINUTE, 543L);
        jGenerator.writeNumberField(Statistics.STATISTIC_REQUESTS_PER_MINUTE_AVG, 54312.0);
        jGenerator.writeNumberField(Statistics.STATISTIC_REQUESTS_PER_MINUTE_MIN, 49L);
        jGenerator.writeNumberField(Statistics.STATISTIC_REQUESTS_PER_MINUTE_MAX, 4753L);
        jGenerator.writeEndObject();
        jGenerator.close();
        String jsonString = new String(stream.toByteArray(), "UTF-8");
        Statistics stat = Statistics.fromJson(jsonString);

        Assert.assertNotNull(stat);
        Assert.assertEquals(10L, stat.getMinutes());
        Assert.assertEquals(100L, stat.getRequests());
        Assert.assertEquals(543L, stat.getRequestsPerMinute());
        Assert.assertEquals(54312.0, stat.getRequestsPerMinuteAvg(), 0.000001);
        Assert.assertEquals(49L, stat.getRequestsPerMinuteMin());
        Assert.assertEquals(4753L, stat.getRequestsPerMinuteMax());
    }

    /**
     * Test of toJson method, of class Statistics.
     */
    @Test
    public void testToJsonOnClass() throws IOException {
        checkToJson(new Function<Statistics, String>() {
            @Override
            public String apply(Statistics t) {
                try {
                    return Statistics.toJson(t);
                } catch (JsonProcessingException e) {
                    Assert.fail(e.toString());
                }
                return null;
            }
        });
    }

    private Long getJsonLongField(String jsonString, String name) throws IOException {
        JsonFactory jfactory = new JsonFactory();
        JsonParser jParser = jfactory.createParser(jsonString);
        Long value = null;

        Assert.assertNotNull(jParser);

        JsonToken token;
        while ((token = jParser.nextToken()) != JsonToken.END_OBJECT) {
            if (token == JsonToken.START_OBJECT) {
                while (jParser.nextToken() != JsonToken.END_OBJECT) {
                }
            }

            if (token != JsonToken.FIELD_NAME)
                continue;

            String fieldname = jParser.getCurrentName();
            if (name.equals(fieldname)) {
                jParser.nextToken();
                value = jParser.getLongValue();
                break;
            }
        }
        jParser.close();

        return value;
    }

    protected void checkToJson(Function<Statistics, String> callback) throws IOException {
        Statistics stats = new Statistics();
        stats.setRequests(145L);
        stats.setMinutes(-325L);
        stats.setRequestsPerMinute(1000L);
        stats.setRequestsPerMinuteMin(107L);
        stats.setRequestsPerMinuteMax(106L);
        String result = callback.apply(stats);
        Assert.assertNotNull(result);

        Assert.assertNotNull(getJsonLongField(result, Statistics.STATISTIC_MINUTES));
        Assert.assertEquals(-325L, (long) getJsonLongField(result, Statistics.STATISTIC_MINUTES));
        Assert.assertNotNull(getJsonLongField(result, Statistics.STATISTIC_REQUESTS));
        Assert.assertEquals(145L, (long) getJsonLongField(result, Statistics.STATISTIC_REQUESTS));
        Assert.assertNotNull(getJsonLongField(result, Statistics.STATISTIC_REQUESTS_PER_MINUTE));
        Assert.assertEquals(1000L, (long) getJsonLongField(result, Statistics.STATISTIC_REQUESTS_PER_MINUTE));
        Assert.assertNotNull(getJsonLongField(result, Statistics.STATISTIC_REQUESTS_PER_MINUTE_MIN));
        Assert.assertEquals(107L, (long) getJsonLongField(result, Statistics.STATISTIC_REQUESTS_PER_MINUTE_MIN));
        Assert.assertNotNull(getJsonLongField(result, Statistics.STATISTIC_REQUESTS_PER_MINUTE_MAX));
        Assert.assertEquals(106L, (long) getJsonLongField(result, Statistics.STATISTIC_REQUESTS_PER_MINUTE_MAX));
    }
}
