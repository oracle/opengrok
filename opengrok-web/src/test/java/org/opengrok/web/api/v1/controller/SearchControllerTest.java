package org.opengrok.web.api.v1.controller;

import org.glassfish.jersey.test.JerseyTest;
import org.junit.*;
import org.opengrok.indexer.condition.ConditionalRunRule;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.index.Indexer;
import org.opengrok.indexer.util.TestRepository;
import org.opengrok.web.api.v1.RestApp;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.opengrok.web.api.v1.filter.CorsFilter.ALLOW_CORS_HEADER;
import static org.opengrok.web.api.v1.filter.CorsFilter.CORS_REQUEST_HEADER;

public class SearchControllerTest extends JerseyTest {
    @ClassRule
    public static ConditionalRunRule rule = new ConditionalRunRule();

    private static final RuntimeEnvironment env = RuntimeEnvironment.getInstance();


    private static TestRepository repository;

    @Override
    protected Application configure() {
        return new RestApp();
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true"); // necessary to test CORS from controllers
        repository = new TestRepository();

        repository.create(SearchControllerTest.class.getResourceAsStream("/org/opengrok/indexer/index/source.zip"));

        env.setHistoryEnabled(false);
        env.setProjectsEnabled(true);
        env.setDefaultProjectsFromNames(Collections.singleton("__all__"));

        env.getSuggesterConfig().setRebuildCronConfig(null);
    }

    @AfterClass
    public static void tearDownClass() {
        repository.destroy();
    }

    @Before
    public void before() {

    }

    @Test
    public void testSearchCors() {
        Response response = target(SearchController.PATH)
                .request()
                .header(CORS_REQUEST_HEADER, "http://example.com")
                .get();
        assertEquals("*", response.getHeaderString(ALLOW_CORS_HEADER));
    }

}
