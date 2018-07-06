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
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.web.suggester.provider.service.impl;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import org.apache.lucene.search.Query;
import org.opengrok.suggest.LookupResultItem;
import org.opengrok.suggest.Suggester;
import org.opengrok.suggest.Suggester.NamedIndexReader;
import org.opengrok.suggest.query.SuggesterQuery;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.SuggesterConfig;
import org.opensolaris.opengrok.index.IndexDatabase;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.web.suggester.provider.service.SuggesterService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SuggesterServiceImpl implements SuggesterService {

    private static final Logger logger = LoggerFactory.getLogger(SuggesterServiceImpl.class);

    private Suggester suggester;

    private static SuggesterServiceImpl instance;

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();

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
    public List<LookupResultItem> getSuggestions(
            final Collection<String> projects,
            final SuggesterQuery suggesterQuery,
            final Query query
    ) {
        rwl.readLock().lock();
        try {
            if (suggester == null) {
                return Collections.emptyList();
            }
            List<NamedIndexReader> namedReaders = getNamedIndexReaders(projects);

            return suggester.search(namedReaders, suggesterQuery, query);
        } finally {
            rwl.readLock().unlock();
        }
    }

    private List<NamedIndexReader> getNamedIndexReaders(final Collection<String> projects) {
        return projects.stream().map(project -> {
            try {
                return new NamedIndexReader(project, env.getIndexSearcher(project).getIndexReader());
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not get index reader for {0}", project);
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public void refresh(final Configuration configuration) {
        rwl.writeLock().lock();
        try {
            suggester.close();
            initSuggester();
        } finally {
            rwl.writeLock().unlock();
        }
    }

    @Override
    public void refresh(final String project) {
        Configuration config = env.getConfiguration();

        Project p = config.getProjects().get(project);
        suggester.rebuild(Collections.singletonList(Paths.get(config.getDataRoot(), IndexDatabase.INDEX_DIR, p.getPath())));
    }

    @Override
    public void delete(final String project) {
        suggester.remove(Collections.singleton(project));
    }

    @Override
    public void onSearch(final Iterable<String> projects, final Query q) {
        suggester.onSearch(projects, q);
    }

    private void initSuggester() {
        Configuration config = env.getConfiguration();

        SuggesterConfig suggesterConfig = config.getSuggester();

        File suggesterDir = new File(config.getDataRoot(), IndexDatabase.SUGGESTER_DIR);
        suggester = new Suggester(suggesterDir,
                suggesterConfig.getMaxResults(),
                Duration.ofSeconds(suggesterConfig.getSuggesterBuildTerminationTimeSec()),
                suggesterConfig.isAllowMostPopular());

        new Thread(() -> {
            suggester.init(getAllProjectIndexDirs());
            scheduleRebuild();
        }).start();
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

    private Duration getTimeToNextRebuild() {
        Configuration config = env.getConfiguration();

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
