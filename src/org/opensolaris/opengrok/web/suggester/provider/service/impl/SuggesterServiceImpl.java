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
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.opengrok.suggest.LookupResultItem;
import org.opengrok.suggest.Suggester;
import org.opengrok.suggest.Suggester.NamedIndexReader;
import org.opengrok.suggest.query.SuggesterQuery;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.SuggesterConfig;
import org.opensolaris.opengrok.configuration.SuperIndexSearcher;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Implementation of {@link SuggesterService}.
 */
public class SuggesterServiceImpl implements SuggesterService {

    private static final Logger logger = LoggerFactory.getLogger(SuggesterServiceImpl.class);

    private static SuggesterServiceImpl instance;

    private Suggester suggester;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private ScheduledFuture<?> future;

    private final RuntimeEnvironment env = RuntimeEnvironment.getInstance();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private SuggesterServiceImpl() {
    }

    public static SuggesterService getInstance() {
        if (instance == null) {
            instance = new SuggesterServiceImpl();
            instance.initSuggester();
        }
        return instance;
    }

    /** {@inheritDoc} */
    @Override
    public List<LookupResultItem> getSuggestions(
            final Collection<String> projects,
            final SuggesterQuery suggesterQuery,
            final Query query
    ) {
        List<SuperIndexSearcher> superIndexSearchers = new LinkedList<>();
        lock.readLock().lock();
        try {
            if (suggester == null) {
                return Collections.emptyList();
            }
            List<NamedIndexReader> namedReaders = getNamedIndexReaders(projects, superIndexSearchers);

            return suggester.search(namedReaders, suggesterQuery, query);
        } finally {
            lock.readLock().unlock();

            for (SuperIndexSearcher s : superIndexSearchers) {
                try {
                    s.getSearcherManager().release(s);
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Could not release " + s, e);
                }
            }
        }
    }

    private List<NamedIndexReader> getNamedIndexReaders(
            final Collection<String> projects,
            final List<SuperIndexSearcher> superIndexSearchers
    ) {
        if (env.getConfiguration().isProjectsEnabled()) {
            return projects.stream().map(project -> {
                try {
                    SuperIndexSearcher searcher = env.getIndexSearcher(project);
                    superIndexSearchers.add(searcher);
                    return new NamedIndexReader(project, searcher.getIndexReader());
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Could not get index reader for " + project, e);
                }
                return null;
            }).filter(Objects::nonNull).collect(Collectors.toList());
        } else {
            SuperIndexSearcher searcher;
            try {
                searcher = env.getIndexSearcher("");
                superIndexSearchers.add(searcher);
                return Collections.singletonList(new NamedIndexReader("", searcher.getIndexReader()));
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not get index reader", e);
            }
            return Collections.emptyList();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void refresh(final Configuration configuration) {
        lock.writeLock().lock();
        try {
            suggester.close();
            initSuggester();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void refresh(final String project) {
        Configuration config = env.getConfiguration();

        Project p = config.getProjects().get(project);
        suggester.rebuild(Collections.singletonList(Paths.get(config.getDataRoot(), IndexDatabase.INDEX_DIR, p.getPath())));
    }

    /** {@inheritDoc} */
    @Override
    public void delete(final String project) {
        suggester.remove(Collections.singleton(project));
    }

    /** {@inheritDoc} */
    @Override
    public void onSearch(final Iterable<String> projects, final Query q) {
        suggester.onSearch(projects, q);
    }

    /** {@inheritDoc} */
    @Override
    public void increaseSearchCount(final String project, final Term term, final int value) {
        suggester.increaseSearchCount(project, term, value);
    }

    private void initSuggester() {
        Configuration config = env.getConfiguration();

        SuggesterConfig suggesterConfig = config.getSuggesterConfig();

        File suggesterDir = new File(config.getDataRoot(), IndexDatabase.SUGGESTER_DIR);
        suggester = new Suggester(suggesterDir,
                suggesterConfig.getMaxResults(),
                Duration.ofSeconds(suggesterConfig.getSuggesterBuildTerminationTimeSec()),
                suggesterConfig.isAllowMostPopular(), env.isProjectsEnabled());

        new Thread(() -> {
            suggester.init(getAllProjectIndexDirs());
            scheduleRebuild();
        }).start();
    }

    private static List<Path> getAllProjectIndexDirs() {
        Configuration config = RuntimeEnvironment.getInstance().getConfiguration();

        if (config.isProjectsEnabled()) {
            return RuntimeEnvironment.getInstance().getProjectList().stream()
                    .filter(Project::isIndexed)
                    .map(project -> Paths.get(config.getDataRoot(), IndexDatabase.INDEX_DIR, project.getPath()))
                    .collect(Collectors.toList());
        } else {
            return Collections.singletonList(Paths.get(
                    RuntimeEnvironment.getInstance().getDataRootPath(), IndexDatabase.INDEX_DIR));
        }
    }

    private Runnable getRebuildAllProjectsRunnable() {
        return () -> {
            suggester.rebuild(getAllProjectIndexDirs());
            scheduleRebuild();
        };
    }

    private void scheduleRebuild() {
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }

        Duration timeToNextRebuild = getTimeToNextRebuild();
        if (timeToNextRebuild == null) {
            logger.log(Level.INFO, "Suggester rebuild not scheduled");
            return;
        }

        logger.log(Level.INFO, "Scheduling suggester rebuild in {0}", timeToNextRebuild);

        future = instance.scheduler.schedule(instance.getRebuildAllProjectsRunnable(), timeToNextRebuild.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private Duration getTimeToNextRebuild() {
        String cronDefinition = env.getConfiguration().getSuggesterConfig().getRebuildCronConfig();
        if (cronDefinition == null) {
            return null;
        }

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

    /** {@inheritDoc} */
    @Override
    public void close() {
        scheduler.shutdownNow();
    }

}
