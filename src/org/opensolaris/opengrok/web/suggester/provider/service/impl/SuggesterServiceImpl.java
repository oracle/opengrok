package org.opensolaris.opengrok.web.suggester.provider.service.impl;

import org.apache.lucene.search.Query;
import org.opengrok.suggest.LookupResultItem;
import org.opengrok.suggest.SuggestersHolder;
import org.opengrok.suggest.query.SuggesterQuery;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.index.IndexDatabase;
import org.opensolaris.opengrok.web.suggester.provider.service.SuggesterService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SuggesterServiceImpl implements SuggesterService {

    private SuggestersHolder suggester;

    private static SuggesterServiceImpl instance;

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

            new Thread(() -> suggester.init(getAllProjectIndexDirs())).start();
        }
    }

    private List<Path> getAllProjectIndexDirs() {
        Configuration config = RuntimeEnvironment.getInstance().getConfiguration();
        if (config == null) {
            return Collections.emptyList();
        }

        return RuntimeEnvironment.getInstance().getProjectList().stream()
                .filter(Project::isIndexed)
                .map(project -> Paths.get(config.getDataRoot(), IndexDatabase.INDEX_DIR, project.getPath()))
                .collect(Collectors.toList());
    }

}
