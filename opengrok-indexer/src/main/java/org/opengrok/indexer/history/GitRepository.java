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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
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
import org.jetbrains.annotations.Nullable;
import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.configuration.RuntimeEnvironment;
import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.ForbiddenSymlinkException;

import static org.opengrok.indexer.history.History.TAGS_SEPARATOR;

/**
 * Access to a Git repository.
 *
 */
public class GitRepository extends RepositoryWithHistoryTraversal {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitRepository.class);

    private static final long serialVersionUID = -6126297612958508386L;

    public static final int GIT_ABBREV_LEN = 8;
    public static final int MAX_CHANGESETS = 65536;

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

            // A RevWalk allows walking over commits based on some filtering that is defined.
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                // and using commit's tree find the path
                RevTree tree = commit.getTree();

                // Now try to find a specific file.
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(tree);
                    treeWalk.setRecursive(true);
                    treeWalk.setFilter(PathFilter.create(filename));
                    if (!treeWalk.next()) {
                        LOGGER.log(Level.FINEST, "Did not find expected file ''{0}'' in revision {1} " +
                                "for repository ''{2}''", new Object[] {filename, rev, directory});
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

        String fullPath;
        try {
            fullPath = new File(parent, basename).getCanonicalPath();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e, () -> String.format(
                    "Failed to get canonical path: %s/%s", parent, basename));
            return false;
        }

        HistoryRevResult result = getHistoryRev(out, fullPath, rev);
        if (!result.success && result.iterations < 1) {
            /*
             * If we failed to get the contents it might be that the file was
             * renamed, so we need to find its original name in that revision
             * and retry with the original name.
             */
            String origPath;
            try {
                origPath = findOriginalName(fullPath, rev);
            } catch (IOException exp) {
                LOGGER.log(Level.SEVERE, exp, () -> String.format(
                        "Failed to get original revision: %s/%s (revision %s)",
                        parent, basename, rev));
                return false;
            }

            if (origPath != null) {
                String fullRenamedPath;
                try {
                    fullRenamedPath = Paths.get(getCanonicalDirectoryName(), origPath).toString();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, e, () -> String.format(
                            "Failed to get canonical path: .../%s", origPath));
                    return false;
                }
                if (!fullRenamedPath.equals(fullPath)) {
                    result = getHistoryRev(out, fullRenamedPath, rev);
                }
            }
        }

        return result.success;
    }

    private String getPathRelativeToCanonicalRepositoryRoot(String fullPath) throws IOException {
        String repoPath = getCanonicalDirectoryName() + File.separator;
        if (fullPath.startsWith(repoPath)) {
            return fullPath.substring(repoPath.length());
        }
        return fullPath;
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
        String fileName = Path.of(filePath).getFileName().toString();
        Annotation annotation = getAnnotation(revision, filePath, fileName);

        if (annotation.getRevisions().isEmpty() && isHandleRenamedFiles()) {
            // The file might have changed its location if it was renamed.
            // Try looking up its original name and get the annotation again.
            String origName = findOriginalName(file.getCanonicalPath(), revision);
            if (origName != null) {
                annotation = getAnnotation(revision, origName, fileName);
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
                LOGGER.log(Level.WARNING, "cannot get first revision of ''{0}'' in repository ''{1}''",
                        new Object[] {filePath, getDirectoryName()});
            }
        } catch (IOException | GitAPIException e) {
            LOGGER.log(Level.WARNING,
                    String.format("cannot get first revision of '%s' in repository '%s'",
                            filePath, getDirectoryName()), e);
        }
        return revision;
    }

    @NotNull
    private Annotation getAnnotation(String revision, String filePath, String fileName) throws IOException {
        Annotation annotation = new Annotation(fileName);

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
        return true;
    }

    @Override
    boolean isRepositoryFor(File file, CommandTimeoutType cmdType) {
        if (file.isDirectory()) {
            File f = new File(file, Constants.DOT_GIT);
            // No check for directory or file as submodules contain '.git' file.
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
        // TODO: check isBare() in JGit ?
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

    @Nullable
    @Override
    public HistoryEntry getLastHistoryEntry(File file, boolean ui) throws HistoryException {
        History hist = getHistory(file, null, null, 1);
        return hist.getLastHistoryEntry();
    }

    public History getHistory(File file, String sinceRevision, String tillRevision) throws HistoryException {
        return getHistory(file, sinceRevision, tillRevision, null);
    }

    private static class HistoryCollector {
        List<HistoryEntry> entries;
        Set<String> renamedFiles;

        HistoryCollector() {
            entries = new ArrayList<>();
            renamedFiles = new HashSet<>();
        }

        public void visit(ChangesetInfo changesetInfo) {
            RepositoryWithHistoryTraversal.CommitInfo commit = changesetInfo.commit;
            HistoryEntry historyEntry = new HistoryEntry(commit.revision,
                    commit.date, commit.authorName + " <" + commit.authorEmail + ">",
                    commit.message, true);

            if (changesetInfo.renamedFiles != null) {
                renamedFiles.addAll(changesetInfo.renamedFiles);
            }
            if (changesetInfo.files != null) {
                historyEntry.setFiles(changesetInfo.files);
            }

            entries.add(historyEntry);
        }
    }

    public History getHistory(File file, String sinceRevision, String tillRevision,
                              Integer numCommits) throws HistoryException {

        HistoryCollector historyCollector = new HistoryCollector();
        traverseHistory(file, sinceRevision, tillRevision, numCommits, historyCollector::visit, false);
        History result = new History(historyCollector.entries, historyCollector.renamedFiles);

        // Assign tags to changesets they represent
        // We don't need to check if this repository supports tags,
        // because we know it :-)
        if (RuntimeEnvironment.getInstance().isTagsEnabled()) {
            assignTagsInHistory(result);
        }

        return result;
    }

    public void traverseHistory(File file, String sinceRevision, String tillRevision,
                              Integer numCommits, Consumer<ChangesetInfo> visitor, boolean getAll) throws HistoryException {

        if (numCommits != null && numCommits <= 0) {
            throw new HistoryException("invalid number of commits to retrieve");
        }

        boolean isDirectory = file.isDirectory();

        try (org.eclipse.jgit.lib.Repository repository = getJGitRepository(getDirectoryName());
             RevWalk walk = new RevWalk(repository)) {

            setupWalk(file, sinceRevision, tillRevision, repository, walk);

            int num = 0;
            for (RevCommit commit : walk) {
                // For truly incremental reindex merge commits have to be processed.
                // TODO: maybe the same for renamed files - depends on what happens if renamed file detection is on
                if (!getAll && commit.getParentCount() > 1 && !isMergeCommitsEnabled()) {
                    continue;
                }

                CommitInfo commitInfo = new CommitInfo(commit.getId().abbreviate(GIT_ABBREV_LEN).name(),
                        commit.getAuthorIdent().getWhen(), commit.getAuthorIdent().getName(),
                        commit.getAuthorIdent().getEmailAddress(), commit.getFullMessage());
                if (isDirectory) {
                    SortedSet<String> files = new TreeSet<>();
                    final Set<String> renamedFiles = new HashSet<>();
                    final Set<String> deletedFiles = new HashSet<>();
                    getFilesForCommit(renamedFiles, files, deletedFiles, commit, repository);
                    visitor.accept(new ChangesetInfo(commitInfo, files, renamedFiles, deletedFiles));
                } else {
                    visitor.accept(new ChangesetInfo(commitInfo));
                }

                if (numCommits != null && ++num >= numCommits) {
                    break;
                }
            }
        } catch (IOException | ForbiddenSymlinkException e) {
            throw new HistoryException(String.format("failed to get history for ''%s''", file), e);
        }
    }

    private void setupWalk(File file, String sinceRevision, String tillRevision, Repository repository, RevWalk walk)
            throws IOException, ForbiddenSymlinkException {

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
    }

    /**
     * Accumulate list of changed/deleted/renamed files for given commit.
     * @param renamedFiles output: renamed files in this commit (if renamed file handling is enabled)
     * @param changedFiles output: changed files in this commit
     * @param deletedFiles output: deleted files in this commit
     * @param commit RevCommit object
     * @param repository repository object
     * @throws IOException on error traversing the commit tree
     */
    private void getFilesForCommit(Set<String> renamedFiles, SortedSet<String> changedFiles, Set<String> deletedFiles,
                                   RevCommit commit,
                                   Repository repository) throws IOException {

        if (commit.getParentCount() == 0) { // first commit - add all files
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    changedFiles.add(getNativePath(getDirectoryNameRelative()) + File.separator +
                            getNativePath(treeWalk.getPathString()));
                }
            }
        } else {
            getFilesBetweenCommits(repository, commit.getParent(0), commit, changedFiles, renamedFiles, deletedFiles);
        }
    }

    private static String getNativePath(String path) {
        if (!File.separator.equals("/")) {
            return path.replace("/", File.separator);
        }

        return path;
    }

    /**
     * Assemble list of changed/deleted/renamed files between a commit and its parent.
     * @param repository repository object
     * @param oldCommit parent commit
     * @param newCommit new commit (the method assumes oldCommit is its parent)
     * @param changedFiles output: set of changedFiles that changed (excludes renamed changedFiles)
     * @param renamedFiles output: set of renamed files (if renamed handling is enabled)
     * @param deletedFiles output: set of deleted files
     * @throws IOException on I/O problem
     */
    private void getFilesBetweenCommits(org.eclipse.jgit.lib.Repository repository,
                                        RevCommit oldCommit, RevCommit newCommit,
                                        Set<String> changedFiles, Set<String> renamedFiles, Set<String> deletedFiles)
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
                String newPath = getNativePath(getDirectoryNameRelative()) + File.separator +
                        getNativePath(diff.getNewPath());

                // TODO: refactor
                switch (diff.getChangeType()) {
                    case DELETE:
                        if (deletedFiles != null) {
                            // newPath would be "/dev/null"
                            String oldPath = getNativePath(getDirectoryNameRelative()) + File.separator +
                                    getNativePath(diff.getOldPath());
                            deletedFiles.add(oldPath);
                        }
                        break;
                    case RENAME:
                        if (isHandleRenamedFiles()) {
                            renamedFiles.add(newPath);
                            if (deletedFiles != null) {
                                String oldPath = getNativePath(getDirectoryNameRelative()) + File.separator +
                                        getNativePath(diff.getOldPath());
                                deletedFiles.add(oldPath);
                            }
                        }
                        break;
                    default:
                        if (changedFiles != null) {
                            // Added files (ChangeType.ADD) are treated as changed.
                            changedFiles.add(newPath);
                        }
                        break;
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

    /**
     * @param dotGit {@code .git} file
     * @return value of the {@code gitdir} property from the file
     */
    private String getGitDirValue(File dotGit) {
        try (Scanner scanner = new Scanner(dotGit, StandardCharsets.UTF_8)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith(Constants.GITDIR)) {
                    return line.substring(Constants.GITDIR.length());
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "failed to scan the contents of file ''{0}''", dotGit);
        }

        return null;
    }

    private org.eclipse.jgit.lib.Repository getJGitRepository(String directory) throws IOException {
        File dotGitFile = Paths.get(directory, Constants.DOT_GIT).toFile();
        if (dotGitFile.isDirectory()) {
            return FileRepositoryBuilder.create(dotGitFile);
        }

        // Assume this is a sub-module so dotGitFile is a file.
        String gitDirValue = getGitDirValue(dotGitFile);
        if (gitDirValue == null) {
            throw new IOException("cannot get gitDir value from " + dotGitFile);
        }

        // If the gitDir value is relative path, make it absolute.
        // This is necessary for the JGit Repository construction.
        File gitDirFile = new File(gitDirValue);
        if (!gitDirFile.isAbsolute()) {
            gitDirFile = new File(directory, gitDirValue);
        }

        return new FileRepositoryBuilder().setWorkTree(new File(directory)).setGitDir(gitDirFile).build();
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
                        LOGGER.log(Level.FINEST,
                                String.format("cannot get tags for \"%s\"", directory.getAbsolutePath()), e);
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
            LOGGER.log(Level.WARNING, String.format("cannot get tags for \"%s\"", directory.getAbsolutePath()), e);
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
    @Nullable
    String determineParent(CommandTimeoutType cmdType) throws IOException {
        try (org.eclipse.jgit.lib.Repository repository = getJGitRepository(getDirectoryName())) {
            if (repository.getConfig() != null) {
                return repository.getConfig().getString("remote", Constants.DEFAULT_REMOTE_NAME, "url");
            } else {
                return null;
            }
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
