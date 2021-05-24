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
 * Copyright (c) 2008, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 * Portions Copyright (c) 2019, Krystof Tulinger <k.tulinger@seznam.cz>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.CountingOutputStream;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.jetbrains.annotations.NotNull;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.ForbiddenSymlinkException;

import static org.opengrok.indexer.history.HistoryEntry.TAGS_SEPARATOR;

/**
 * Access to a Git repository.
 *
 */
public class GitRepository extends RepositoryWithPerPartesHistory {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitRepository.class);

    private static final long serialVersionUID = -6126297612958508386L;

    public static final int GIT_ABBREV_LEN = 8;
    public static final int MAX_CHANGESETS = 512;

    public GitRepository() {
        type = "git";

        ignoredDirs.add(".git");
        ignoredFiles.add(".git");
    }

    /**
     * Be careful, git uses only forward slashes in its command and output (not in file path).
     * Using backslashes together with git show will get empty output and 0 status code.
     * @return string with separator characters replaced with forward slash
     */
    private static String getGitFilePath(String filePath) {
        return filePath.replace(File.separatorChar, '/');
    }

    /**
     * Try to get file contents for given revision.
     *
     * @param out a required OutputStream
     * @param fullpath full pathname of the file
     * @param rev revision string
     * @return a defined instance with {@code success} == {@code true} if no
     * error occurred and with non-zero {@code iterations} if some data was transferred
     */
    private HistoryRevResult getHistoryRev(OutputStream out, String fullpath, String rev) {

        HistoryRevResult result = new HistoryRevResult();
        File directory = new File(getDirectoryName());

        String filename;
        result.success = false;
        try {
            filename = getGitFilePath(Paths.get(getCanonicalDirectoryName()).relativize(Paths.get(fullpath)).toString());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, String.format("Failed to relativize '%s' in for repository '%s'",
                    fullpath, directory), e);
            return result;
        }

        try (org.eclipse.jgit.lib.Repository repository = getJGitRepository(directory.getAbsolutePath())) {
            ObjectId commitId = repository.resolve(rev);

            // a RevWalk allows to walk over commits based on some filtering that is defined
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                // and using commit's tree find the path
                RevTree tree = commit.getTree();

                // now try to find a specific file
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(tree);
                    treeWalk.setRecursive(true);
                    treeWalk.setFilter(PathFilter.create(filename));
                    if (!treeWalk.next()) {
                        LOGGER.log(Level.FINEST,
                                String.format("Did not find expected file '%s' in revision %s for repository '%s'",
                                        filename, rev, directory));
                        return result;
                    }

                    ObjectId objectId = treeWalk.getObjectId(0);
                    ObjectLoader loader = repository.open(objectId);

                    CountingOutputStream countingOutputStream = new CountingOutputStream(out);
                    loader.copyTo(countingOutputStream);
                    result.iterations = countingOutputStream.getCount();
                    result.success = true;
                }

                revWalk.dispose();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, String.format("Failed to get file '%s' in revision %s for repository '%s'",
                    filename, rev, directory), e);
        }

        return result;
    }

    @Override
    boolean getHistoryGet(OutputStream out, String parent, String basename, String rev) {

        String fullpath;
        try {
            fullpath = new File(parent, basename).getCanonicalPath();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e, () -> String.format(
                    "Failed to get canonical path: %s/%s", parent, basename));
            return false;
        }

        HistoryRevResult result = getHistoryRev(out, fullpath, rev);
        if (!result.success && result.iterations < 1) {
            /*
             * If we failed to get the contents it might be that the file was
             * renamed so we need to find its original name in that revision
             * and retry with the original name.
             */
            String origpath;
            try {
                origpath = findOriginalName(fullpath, rev);
            } catch (IOException exp) {
                LOGGER.log(Level.SEVERE, exp, () -> String.format(
                        "Failed to get original revision: %s/%s (revision %s)",
                        parent, basename, rev));
                return false;
            }

            if (origpath != null) {
                String fullRenamedPath;
                try {
                    fullRenamedPath = Paths.get(getCanonicalDirectoryName(), origpath).toString();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, e, () -> String.format(
                            "Failed to get canonical path: .../%s", origpath));
                    return false;
                }
                if (!fullRenamedPath.equals(fullpath)) {
                    result = getHistoryRev(out, fullRenamedPath, rev);
                }
            }
        }

        return result.success;
    }

    /**
     * Create a {@code Reader} that reads an {@code InputStream} using the
     * correct character encoding.
     *
     * @param input a stream with the output from a log or blame command
     * @return a reader that reads the input
     */
    static Reader newLogReader(InputStream input) {
        // Bug #17731: Git always encodes the log output using UTF-8 (unless
        // overridden by i18n.logoutputencoding, but let's assume that hasn't
        // been done for now). Create a reader that uses UTF-8 instead of the
        // platform's default encoding.
        return new InputStreamReader(input, StandardCharsets.UTF_8);
    }

    private String getPathRelativeToCanonicalRepositoryRoot(String fullpath)
            throws IOException {
        String repoPath = getCanonicalDirectoryName() + File.separator;
        if (fullpath.startsWith(repoPath)) {
            return fullpath.substring(repoPath.length());
        }
        return fullpath;
    }

    /**
     * Get the name of file in given revision. The returned file name is relative to the repository root.
     * Assumes renamed file hanndling is on.
     *
     * @param fullpath full file path
     * @param changeset revision ID (could be short)
     * @return original filename relative to the repository root
     * @throws java.io.IOException if I/O exception occurred
     * @see #getPathRelativeToCanonicalRepositoryRoot(String)
     */
    String findOriginalName(String fullpath, String changeset) throws IOException {

        if (fullpath == null || fullpath.isEmpty()) {
            throw new IOException(String.format("Invalid file path string: %s", fullpath));
        }

        if (changeset == null || changeset.isEmpty()) {
            throw new IOException(String.format("Invalid changeset string for path %s: %s",
                    fullpath, changeset));
        }

        String fileInRepo = getGitFilePath(getPathRelativeToCanonicalRepositoryRoot(fullpath));

        String originalFile = fileInRepo;
        try (org.eclipse.jgit.lib.Repository repository = getJGitRepository(getDirectoryName());
             RevWalk walk = new RevWalk(repository)) {

            walk.markStart(walk.parseCommit(repository.resolve(Constants.HEAD)));
            walk.markUninteresting(walk.lookupCommit(repository.resolve(changeset)));

            Config config = repository.getConfig();
            config.setBoolean("diff", null, "renames", true);
            org.eclipse.jgit.diff.DiffConfig dc = config.get(org.eclipse.jgit.diff.DiffConfig.KEY);
            FollowFilter followFilter = FollowFilter.create(getGitFilePath(fileInRepo), dc);
            walk.setTreeFilter(followFilter);

            for (RevCommit commit : walk) {
                if (commit.getParentCount() > 1 && !isMergeCommitsEnabled()) {
                    continue;
                }

                if (commit.getId().getName().startsWith(changeset)) {
                    break;
                }

                if (commit.getParentCount() >= 1) {
                    OutputStream outputStream = NullOutputStream.INSTANCE;
                    try (DiffFormatter formatter = new DiffFormatter(outputStream)) {
                        formatter.setRepository(repository);
                        formatter.setDetectRenames(true);

                        List<DiffEntry> diffs = formatter.scan(prepareTreeParser(repository, commit.getParent(0)),
                                prepareTreeParser(repository, commit));

                        for (DiffEntry diff : diffs) {
                            if (diff.getChangeType() == DiffEntry.ChangeType.RENAME &&
                                    originalFile.equals(diff.getNewPath())) {
                                originalFile = diff.getOldPath();
                            }
                        }
                    }
                }
            }
        }

        if (originalFile == null) {
            LOGGER.log(Level.WARNING, "Failed to get original name in revision {0} for: \"{1}\"",
                    new Object[]{changeset, fullpath});
            return null;
        }

        return getNativePath(originalFile);
    }

    /**
     * Annotate the specified file/revision.
     *
     * @param file file to annotate
     * @param revision revision to annotate
     * @return file annotation or {@code null}
     * @throws java.io.IOException if I/O exception occurred
     */
    @Override
    public Annotation annotate(File file, String revision) throws IOException {
        String filePath = getPathRelativeToCanonicalRepositoryRoot(file.getCanonicalPath());

        if (revision == null) {
            revision = getFirstRevision(filePath);
        }
        Annotation annotation = getAnnotation(revision, filePath);

        if (annotation.getRevisions().isEmpty() && isHandleRenamedFiles()) {
            // The file might have changed its location if it was renamed.
            // Try to lookup its original name and get the annotation again.
            String origName = findOriginalName(file.getCanonicalPath(), revision);
            if (origName != null) {
                annotation = getAnnotation(revision, origName);
            }
        }

        return annotation;
    }

    private String getFirstRevision(String filePath) {
        String revision = null;
        try (org.eclipse.jgit.lib.Repository repository = getJGitRepository(getDirectoryName())) {
            Iterable<RevCommit> commits = new Git(repository).log().
                    addPath(getGitFilePath(filePath)).
                    setMaxCount(1).
                    call();
            RevCommit commit = commits.iterator().next();
            if (commit != null) {
                revision = commit.getId().getName();
            } else {
                LOGGER.log(Level.WARNING, String.format("cannot get first revision of '%s' in repository '%s'",
                        filePath, getDirectoryName()));
            }
        } catch (IOException | GitAPIException e) {
            LOGGER.log(Level.WARNING,
                    String.format("cannot get first revision of '%s' in repository '%s'",
                            filePath, getDirectoryName()), e);
        }
        return revision;
    }

    @NotNull
    private Annotation getAnnotation(String revision, String filePath) throws IOException {
        Annotation annotation = new Annotation(filePath);

        try (org.eclipse.jgit.lib.Repository repository = getJGitRepository(getDirectoryName())) {
            BlameCommand blameCommand = new Git(repository).blame().setFilePath(getGitFilePath(filePath));
            ObjectId commitId = repository.resolve(revision);
            blameCommand.setStartCommit(commitId);
            blameCommand.setFollowFileRenames(isHandleRenamedFiles());
            final BlameResult result = blameCommand.setTextComparator(RawTextComparator.WS_IGNORE_ALL).call();
            if (result != null) {
                final RawText rawText = result.getResultContents();
                for (int i = 0; i < rawText.size(); i++) {
                    final PersonIdent sourceAuthor = result.getSourceAuthor(i);
                    final RevCommit sourceCommit = result.getSourceCommit(i);
                    annotation.addLine(sourceCommit.getId().abbreviate(GIT_ABBREV_LEN).
                            name(), sourceAuthor.getName(), true);
                }
            }
        } catch (GitAPIException e) {
            LOGGER.log(Level.FINER,
                    String.format("failed to get annotation for file '%s' in repository '%s' in revision '%s'",
                            filePath, getDirectoryName(), revision));
        }
        return annotation;
    }

    @Override
    public boolean fileHasAnnotation(File file) {
        return true;
    }

    @Override
    public boolean fileHasHistory(File file) {
        // Todo: is there a cheap test for whether Git has history
        // available for a file?
        // Otherwise, this is harmless, since Git's commands will just
        // print nothing if there is no history.
        return true;
    }

    @Override
    boolean isRepositoryFor(File file, CommandTimeoutType cmdType) {
        if (file.isDirectory()) {
            File f = new File(file, ".git");
            return f.exists();
        }
        return false;
    }

    @Override
    boolean supportsSubRepositories() {
        return true;
    }

    /**
     * Gets a value indicating the instance is nestable.
     * @return {@code true}
     */
    @Override
    boolean isNestable() {
        return true;
    }

    @Override
    public boolean isWorking() {
        return true;
    }

    @Override
    boolean hasHistoryForDirectories() {
        return true;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        return getHistory(file, null);
    }

    @Override
    History getHistory(File file, String sinceRevision) throws HistoryException {
        return getHistory(file, sinceRevision, null);
    }

    public int getPerPartesCount() {
        return MAX_CHANGESETS;
    }

    public void accept(String sinceRevision, Consumer<String> visitor) throws HistoryException {
        try (org.eclipse.jgit.lib.Repository repository = getJGitRepository(getDirectoryName());
             RevWalk walk = new RevWalk(repository)) {

            if (sinceRevision != null) {
                walk.markUninteresting(walk.lookupCommit(repository.resolve(sinceRevision)));
            }
            walk.markStart(walk.parseCommit(repository.resolve(Constants.HEAD)));

            for (RevCommit commit : walk) {
                // Do not abbreviate the Id as this could cause AmbiguousObjectException in getHistory().
                visitor.accept(commit.getId().name());
            }
        } catch (IOException e) {
            throw new HistoryException(e);
        }
    }

    public History getHistory(File file, String sinceRevision, String tillRevision) throws HistoryException {
        final List<HistoryEntry> entries = new ArrayList<>();
        final Set<String> renamedFiles = new HashSet<>();

        boolean isDirectory = file.isDirectory();

        try (org.eclipse.jgit.lib.Repository repository = getJGitRepository(getDirectoryName());
             RevWalk walk = new RevWalk(repository)) {

            if (sinceRevision != null) {
                walk.markUninteresting(walk.lookupCommit(repository.resolve(sinceRevision)));
            }

            if (tillRevision != null) {
                walk.markStart(walk.lookupCommit(repository.resolve(tillRevision)));
            } else {
                walk.markStart(walk.parseCommit(repository.resolve(Constants.HEAD)));
            }

            String relativePath = RuntimeEnvironment.getInstance().getPathRelativeToSourceRoot(file);
            if (!getDirectoryNameRelative().equals(relativePath)) {
                if (isHandleRenamedFiles()) {
                    Config config = repository.getConfig();
                    config.setBoolean("diff", null, "renames", true);
                    org.eclipse.jgit.diff.DiffConfig dc = config.get(org.eclipse.jgit.diff.DiffConfig.KEY);
                    FollowFilter followFilter = FollowFilter.create(getGitFilePath(getRepoRelativePath(file)), dc);
                    walk.setTreeFilter(followFilter);
                } else {
                    walk.setTreeFilter(AndTreeFilter.create(
                            PathFilter.create(getGitFilePath(getRepoRelativePath(file))),
                            TreeFilter.ANY_DIFF));
                }
            }

            for (RevCommit commit : walk) {
                if (commit.getParentCount() > 1 && !isMergeCommitsEnabled()) {
                    continue;
                }

                HistoryEntry historyEntry = new HistoryEntry(commit.getId().abbreviate(GIT_ABBREV_LEN).name(),
                        commit.getAuthorIdent().getWhen(),
                        commit.getAuthorIdent().getName() +
                                " <" + commit.getAuthorIdent().getEmailAddress() + ">",
                        null, commit.getFullMessage(), true);

                if (isDirectory) {
                    SortedSet<String> files = new TreeSet<>();
                    getFilesForCommit(renamedFiles, files, commit, repository);
                    historyEntry.setFiles(files);
                }

                entries.add(historyEntry);
            }
        } catch (IOException | ForbiddenSymlinkException e) {
            throw new HistoryException(String.format("failed to get history for ''%s''", file), e);
        }

        History result = new History(entries, renamedFiles);

        // Assign tags to changesets they represent
        // We don't need to check if this repository supports tags,
        // because we know it :-)
        if (RuntimeEnvironment.getInstance().isTagsEnabled()) {
            assignTagsInHistory(result);
        }

        return result;
    }

    /**
     * Accumulate list of changed files and renamed files (if enabled) for given commit.
     * @param renamedFiles result containing the renamed files in this commit
     * @param files result containing changed files in this commit
     * @param commit RevCommit object
     * @param repository repository object
     * @throws IOException on error traversing the commit tree
     */
    private void getFilesForCommit(Set<String> renamedFiles, SortedSet<String> files, RevCommit commit,
                                   Repository repository) throws IOException {

        int numParents = commit.getParentCount();

        if (numParents == 1) {
            getFiles(repository, commit.getParent(0), commit, files, renamedFiles);
        } else if (numParents == 0) { // first commit
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    files.add(getNativePath(getDirectoryNameRelative()) + File.separator +
                            getNativePath(treeWalk.getPathString()));
                }
            }
        } else {
            getFiles(repository, commit.getParent(0), commit, files, renamedFiles);
        }
    }

    private static String getNativePath(String path) {
        if (!File.separator.equals("/")) {
            return path.replace("/", File.separator);
        }

        return path;
    }

    /**
     * Assemble list of files that changed between 2 commits.
     * @param repository repository object
     * @param oldCommit parent commit
     * @param newCommit new commit (the mehotd assumes oldCommit is its parent)
     * @param files set of files that changed (excludes renamed files)
     * @param renamedFiles set of renamed files (if renamed handling is enabled)
     * @throws IOException on I/O problem
     */
    private void getFiles(org.eclipse.jgit.lib.Repository repository,
                          RevCommit oldCommit, RevCommit newCommit,
                          Set<String> files, Set<String> renamedFiles)
            throws IOException {

        OutputStream outputStream = NullOutputStream.INSTANCE;
        try (DiffFormatter formatter = new DiffFormatter(outputStream)) {
            formatter.setRepository(repository);
            if (isHandleRenamedFiles()) {
                formatter.setDetectRenames(true);
            }

            List<DiffEntry> diffs = formatter.scan(prepareTreeParser(repository, oldCommit),
                    prepareTreeParser(repository, newCommit));

            for (DiffEntry diff : diffs) {
                if (diff.getChangeType() != DiffEntry.ChangeType.DELETE) {
                    if (files != null) {
                        files.add(getNativePath(getDirectoryNameRelative()) + File.separator +
                                getNativePath(diff.getNewPath()));
                    }
                }

                if (diff.getChangeType() == DiffEntry.ChangeType.RENAME && isHandleRenamedFiles()) {
                    renamedFiles.add(getNativePath(getDirectoryNameRelative()) + File.separator +
                            getNativePath(diff.getNewPath()));
                }
            }
        }
    }

    private static AbstractTreeIterator prepareTreeParser(org.eclipse.jgit.lib.Repository repository,
                                                          RevCommit commit) throws IOException {
        // from the commit we can build the tree which allows us to construct the TreeParser
        try (RevWalk walk = new RevWalk(repository)) {
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }

            walk.dispose();

            return treeParser;
        }
    }

    @Override
    boolean hasFileBasedTags() {
        return true;
    }

    private org.eclipse.jgit.lib.Repository getJGitRepository(String directory) throws IOException {
        return FileRepositoryBuilder.create(Paths.get(directory, ".git").toFile());
    }

    private void rebuildTagList(File directory) {
        this.tagList = new TreeSet<>();
        try (org.eclipse.jgit.lib.Repository repository = getJGitRepository(directory.getAbsolutePath())) {
            try (Git git = new Git(repository)) {
                List<Ref> refList = git.tagList().call(); // refs sorted according to tag names
                Map<RevCommit, String> commit2Tags = new HashMap<>();
                for (Ref ref : refList) {
                    try {
                        RevCommit commit = getCommit(repository, ref);
                        String tagName = ref.getName().replace("refs/tags/", "");
                        commit2Tags.merge(commit, tagName, (oldValue, newValue) -> oldValue + TAGS_SEPARATOR + newValue);
                    } catch (IOException e) {
                        LOGGER.log(Level.FINEST, "cannot get tags for \"" + directory.getAbsolutePath() + "\"", e);
                    }
                }

                for (Map.Entry<RevCommit, String> entry : commit2Tags.entrySet()) {
                    int commitTime = entry.getKey().getCommitTime();
                    Date date = new Date((long) (commitTime) * 1000);
                    GitTagEntry tagEntry = new GitTagEntry(entry.getKey().getName(),
                            date, entry.getValue());
                    this.tagList.add(tagEntry);
                }
            }
        } catch (IOException | GitAPIException e) {
            LOGGER.log(Level.WARNING, "cannot get tags for \"" + directory.getAbsolutePath() + "\"", e);
            // In case of partial success, do not null-out tagList here.
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER, "Read tags count={0} for {1}",
                    new Object[] {tagList.size(), directory});
        }
    }

    /**
     * Builds a Git tag list by querying Git commit hash, commit time, and tag
     * names.
     * <p>Repository technically relies on the tag list to be ancestor ordered.
     * <p>For a version control system that uses "linear revision numbering"
     * (e.g. Subversion or Mercurial), the natural ordering in the
     * {@link TreeSet} is by ancestor order and so
     * {@link TagEntry#compareTo(HistoryEntry)} always determines the correct
     * tag.
     * <p>For {@link GitTagEntry} that does not use linear revision numbering,
     * the {@link TreeSet} will be ordered by date. That does not necessarily
     * align with ancestor order. In that case,
     * {@link GitTagEntry#compareTo(HistoryEntry)} that compares by date can
     * find the wrong tag.
     * <p>Linus Torvalds: [When asking] "'can commit X be an ancestor of commit
     * Y' (as a way to basically limit certain algorithms from having to walk
     * all the way down). We've used commit dates for it, and realistically it
     * really has worked very well. But it was always a broken heuristic."
     * <p>"I think the lack of [generation numbers] is literally the only real
     * design mistake we have [in Git]."
     * <p>"We discussed adding generation numbers about 6 years ago [in 2005].
     * We clearly *should* have done it. Instead, we went with the hacky `let's
     * use commit time', that everybody really knew was technically wrong, and
     * was a hack, but avoided the need."
     * <p>If Git ever gets standard generation numbers,
     * {@link GitTagEntry#compareTo(HistoryEntry)} should be revised to work
     * reliably in all cases akin to a version control system that uses "linear
     * revision numbering."
     * @param directory a defined directory of the repository
     * @param cmdType command timeout type
     */
    @Override
    protected void buildTagList(File directory, CommandTimeoutType cmdType) {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<?> future = executor.submit(() -> rebuildTagList(directory));
        executor.shutdown();

        try {
            future.get(RuntimeEnvironment.getInstance().getCommandTimeout(cmdType), TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "failed tag rebuild for directory " + directory, e);
        } catch (TimeoutException e) {
            LOGGER.log(Level.WARNING, "timed out tag rebuild for directory " + directory, e);
        }

        if (!executor.isTerminated()) {
            executor.shutdownNow();
        }
    }

    @NotNull
    private RevCommit getCommit(org.eclipse.jgit.lib.Repository repository, Ref ref) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            return walk.parseCommit(ref.getObjectId());
        }
    }

    @Override
    String determineParent(CommandTimeoutType cmdType) throws IOException {
        try (org.eclipse.jgit.lib.Repository repository = getJGitRepository(getDirectoryName())) {
            return repository.getConfig().getString("remote", "origin", "url");
        }
    }

    @Override
    String determineBranch(CommandTimeoutType cmdType) throws IOException {
        try (org.eclipse.jgit.lib.Repository repository = getJGitRepository(getDirectoryName())) {
            return repository.getBranch();
        }
    }

    @Override
    public String determineCurrentVersion(CommandTimeoutType cmdType) throws IOException {
        try (org.eclipse.jgit.lib.Repository repository = getJGitRepository(getDirectoryName())) {
            Ref head = repository.exactRef(Constants.HEAD);
            if (head != null && head.getObjectId() != null) {
                try (RevWalk walk = new RevWalk(repository); ObjectReader reader = repository.newObjectReader()) {
                    RevCommit commit = walk.parseCommit(head.getObjectId());
                    int commitTime = commit.getCommitTime();
                    Date date = new Date((long) (commitTime) * 1000);
                    return String.format("%s %s %s %s",
                            format(date),
                            reader.abbreviate(head.getObjectId()).name(),
                            commit.getAuthorIdent().getName(),
                            commit.getShortMessage());
                }
            }
        }

        return null;
    }
}
