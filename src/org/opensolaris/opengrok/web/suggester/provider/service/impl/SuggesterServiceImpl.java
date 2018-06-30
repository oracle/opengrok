package org.opensolaris.opengrok.web.suggester.provider.service.impl;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import org.apache.lucene.search.Query;
import org.opengrok.suggest.LookupResultItem;
import org.opengrok.suggest.SuggestersHolder;
import org.opengrok.suggest.query.SuggesterQuery;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.index.IndexDatabase;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.web.suggester.provider.service.SuggesterService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SuggesterServiceImpl implements SuggesterService {

    private static final Logger logger = LoggerFactory.getLogger(SuggesterServiceImpl.class);

    private SuggestersHolder suggester;

    private static SuggesterServiceImpl instance;

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private SuggesterServiceImpl() {
    }

    public static SuggesterService getInstance() {
        if (instance == null) {
            instance = new SuggesterServiceImpl();
            instance.initSuggester();
        }
        return instance;
    }

    @Override
    public List<LookupResultItem> getSuggestions(List<String> projects, SuggesterQuery suggesterQuery, Query query) {
        if (suggester == null) {
            return Collections.emptyList();
        }

        List<SuggestersHolder.NamedIndexReader> namedReaders = projects.stream().map(project ->
        {
            try {
                return new SuggestersHolder.NamedIndexReader(project, RuntimeEnvironment.getInstance().getIndexSearcher(project).getIndexReader());
            } catch (IOException e) {
                e.printStackTrace();
            }
            throw new IllegalStateException();
        }).collect(Collectors.toList());

        return suggester.search(namedReaders, suggesterQuery, query);
    }

    @Override
    public void refresh(String project) {
        Configuration config = RuntimeEnvironment.getInstance().getConfiguration();
        if (config != null) {
            Project p = config.getProjects().get(project);
            suggester.rebuild(Collections.singletonList(Paths.get(config.getDataRoot(), IndexDatabase.INDEX_DIR, p.getPath())));
        }
    }

    @Override
    public void delete(final String project) {
        suggester.remove(Collections.singleton(project));
    }

    @Override
    public void onSearch(Iterable<String> projects, Query q) {
        suggester.onSearch(projects, q);
    }

    private void initSuggester() {
        Configuration config = RuntimeEnvironment.getInstance().getConfiguration();
        if (config != null) {
            suggester = new SuggestersHolder(new File(config.getDataRoot(), IndexDatabase.SUGGESTER_DIR));

            new Thread(() -> {
                suggester.init(getAllProjectIndexDirs());
                scheduleRebuild();
            }).start();
        }
    }

    private static List<Path> getAllProjectIndexDirs() {
        Configuration config = RuntimeEnvironment.getInstance().getConfiguration();
        if (config == null) {
            return Collections.emptyList();
        }

        return RuntimeEnvironment.getInstance().getProjectList().stream()
                .filter(Project::isIndexed)
                .map(project -> Paths.get(config.getDataRoot(), IndexDatabase.INDEX_DIR, project.getPath()))
                .collect(Collectors.toList());
    }

    private Runnable getRebuildAllProjectsRunnable() {
        return () -> {
            suggester.rebuild(getAllProjectIndexDirs());
            scheduleRebuild();
        };
    }

    private void scheduleRebuild() {
        Duration timeToNextRebuild = getTimeToNextRebuild();

        logger.log(Level.INFO, "Scheduling suggester rebuild in {0}", timeToNextRebuild);

        instance.scheduler.schedule(instance.getRebuildAllProjectsRunnable(), timeToNextRebuild.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private static Duration getTimeToNextRebuild() {
        Configuration config = RuntimeEnvironment.getInstance().getConfiguration();
        if (config == null) {
            throw new IllegalStateException("No configuration specified");
        }

        String cronDefinition = config.getSuggester().getRebuildCronConfig();

        ZonedDateTime now = ZonedDateTime.now();

        CronDefinition def = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);

        CronParser parser = new CronParser(def);

        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(cronDefinition));

        Optional<Duration> d = executionTime.timeToNextExecution(now);
        if (!d.isPresent()) {
            throw new IllegalStateException("Cannot determine time to next execution");
        }

        return d.get();
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

}
