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
    public void add() {

    }

    @Override
    public void refresh() {
        if (suggester == null) {
            initSuggester();
        } else {
            // check if source root changed!
        }
    }

    @Override
    public void delete() {

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

    /*private void onReindex() {
        if (suggester == null) {
            logger.log(Level.WARNING, "Cannot refresh suggester because it was not initialized yet");
            return;
        }

        new Thread(() -> suggester.rebuild(getAllProjectIndexDirs())).start();
    }

    private void onReindex(final Set<String> projects) {
        if (projects == null) {
            return;
        }

        if (suggester == null) {
            logger.log(Level.WARNING, "Cannot refresh suggester because it was not initialized yet");
            return;
        }

        Configuration config = RuntimeEnvironment.getInstance().getConfiguration();

        List<Path> indexDirs = projects.stream()
                .map(project -> Paths.get(config.getDataRoot(), IndexDatabase.INDEX_DIR, project))
                .collect(Collectors.toList());

        new Thread(() -> suggester.rebuild(indexDirs)).start();
    }

    private void handleProjectMessage(final ProjectMessage message) {
        String command = message.getText();

        switch (command) {
            case "indexed":
                // rebuild will detect that no suggester index for project exists and will create a new one
                onReindex(message.getTags());
                break;
            case "delete":
                if (suggester != null) {
                    suggester.remove(message.getTags());
                } else {
                    logger.log(Level.WARNING, "Cannot remove project because suggester was not initialized yet");
                }
                break;
            default:
                // ignore
        }
    }

    public void onSearch(Set<String> projects, Query query) {
        suggester.onSearch(projects, query);
    }*/

}
