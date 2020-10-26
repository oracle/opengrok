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
 * Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.index;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.opengrok.indexer.logger.LoggerFactory;
import org.opengrok.indexer.util.Progress;
import org.opengrok.indexer.util.StringUtils;
import org.opengrok.indexer.util.TandemPath;

/**
 * Represents a tracker of pending file deletions and renamings that can later
 * be executed.
 * <p>
 * {@link PendingFileCompleter} is not generally thread-safe, as only
 * {@link #add(org.opengrok.indexer.index.PendingFileRenaming)} is expected
 * to be run in parallel; that method is thread-safe -- but only among other
 * callers of the same method.
 * <p>
 * No methods are thread-safe between each other. E.g.,
 * {@link #complete()} should only be called by a single thread after all
 * additions of {@link PendingSymlinkage}s, {@link PendingFileDeletion}s, and
 * {@link PendingFileRenaming}s are indicated.
 * <p>
 * {@link #add(org.opengrok.indexer.index.PendingSymlinkage)} should only
 * be called in serial from a single thread in an isolated stage.
 * <p>
 * {@link #add(org.opengrok.indexer.index.PendingFileDeletion)} should only
 * be called in serial from a single thread in an isolated stage.
 * <p>
 * {@link #add(org.opengrok.indexer.index.PendingFileRenaming)}, as noted,
 * can be called in parallel in an isolated stage.
 */
class PendingFileCompleter {

    /**
     * An extension that should be used as the suffix of files for
     * {@link PendingFileRenaming} actions.
     * <p>Value is {@code ".org_opengrok"}.
     */
    public static final String PENDING_EXTENSION = ".org_opengrok";

    private static final Logger LOGGER =
        LoggerFactory.getLogger(PendingFileCompleter.class);

    private final Object INSTANCE_LOCK = new Object();

    private volatile boolean completing;

    /**
     * Descending path segment length comparator.
     */
    private static final Comparator<File> DESC_PATHLEN_COMPARATOR =
        (File f1, File f2) -> {
            String s1 = f1.getAbsolutePath();
            String s2 = f2.getAbsolutePath();
            int n1 = countPathSegments(s1);
            int n2 = countPathSegments(s2);
            // DESC: s2 no. of segments <=> s1 no. of segments
            int cmp = Integer.compare(n2, n1);
            if (cmp != 0) {
                return cmp;
            }

            // the Comparator must also be "consistent with equals", so check
            // string contents too when (length)cmp == 0. (ASC: s1 <=> s2.)
            cmp = s1.compareTo(s2);
            return cmp;
    };

    private final Set<PendingFileDeletion> deletions = new HashSet<>();

    private final Set<PendingFileRenaming> renames = new HashSet<>();

    private final Set<PendingSymlinkage> linkages = new HashSet<>();

    /**
     * Adds the specified element to this instance's set if it is not already
     * present.
     * @param e element to be added to this set
     * @return {@code true} if this instance's set did not already contain the
     * specified element
     * @throws IllegalStateException if {@link #complete()} is running
     */
    public boolean add(PendingFileDeletion e) {
        if (completing) {
            throw new IllegalStateException("complete() is running");
        }
        return deletions.add(e);
    }

    /**
     * Adds the specified element to this instance's set if it is not already
     * present.
     * @param e element to be added to this set
     * @return {@code true} if this instance's set did not already contain the
     * specified element
     * @throws IllegalStateException if {@link #complete()} is running
     */
    public boolean add(PendingSymlinkage e) {
        if (completing) {
            throw new IllegalStateException("complete() is running");
        }
        return linkages.add(e);
    }

    /**
     * Adds the specified element to this instance's set if it is not already
     * present, and also remove any pending deletion for the same absolute
     * path -- all in a thread-safe manner among other callers of this same
     * method (and only this method).
     * @param e element to be added to this set
     * @return {@code true} if this instance's set did not already contain the
     * specified element
     * @throws IllegalStateException if {@link #complete()} is running
     */
    public boolean add(PendingFileRenaming e) {
        if (completing) {
            throw new IllegalStateException("complete() is running");
        }
        synchronized (INSTANCE_LOCK) {
            boolean rc = renames.add(e);
            deletions.remove(new PendingFileDeletion(e.getAbsolutePath()));
            return rc;
        }
    }

    /**
     * Complete all the tracked file operations: first in a stage for pending
     * deletions, next in a stage for pending renamings, and finally in a stage
     * for pending symbolic linkages.
     * <p>
     * All operations in each stage are tried in parallel, and any failure is
     * caught and raises an exception (after all items in the stage have been
     * tried).
     * <p>
     * Deletions are tried for each
     * {@link PendingFileDeletion#getAbsolutePath()}; for a version of the path
     * with {@link #PENDING_EXTENSION} appended; and also for the path's parent
     * directory, which does nothing if the directory is not empty.
     * @return the number of successful operations
     * @throws java.io.IOException if an I/O error occurs
     */
    public int complete() throws IOException {
        completing = true;
        try {
            return completeInner();
        } finally {
            completing = false;
        }
    }

    private int completeInner() throws IOException {
        Instant start = Instant.now();
        int numDeletions = completeDeletions();
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "deleted {0} file(s) (took {1})",
                    new Object[] {numDeletions, StringUtils.getReadableTime(
                            Duration.between(start, Instant.now()).toMillis())});
        }

        start = Instant.now();
        int numRenamings = completeRenamings();
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "renamed {0} file(s) (took {1})",
                    new Object[] {numRenamings, StringUtils.getReadableTime(
                            Duration.between(start, Instant.now()).toMillis())});
        }

        start = Instant.now();
        int numLinkages = completeLinkages();
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "affirmed links for {0} path(s) (took {1})",
                    new Object[] {numLinkages, StringUtils.getReadableTime(
                            Duration.between(start, Instant.now()).toMillis())});
        }

        return numDeletions + numRenamings + numLinkages;
    }

    /**
     * Attempts to rename all the tracked elements, catching any failures, and
     * throwing an exception if any failed.
     * @return the number of successful renamings
     */
    private int completeRenamings() throws IOException {
        int numPending = renames.size();
        int numFailures = 0;

        if (numPending < 1) {
            return 0;
        }

        List<PendingFileRenamingExec> pendingExecs = renames.
            parallelStream().map(f ->
            new PendingFileRenamingExec(f.getTransientPath(),
                f.getAbsolutePath())).collect(
            Collectors.toList());
        Map<Boolean, List<PendingFileRenamingExec>> bySuccess;

        try (Progress progress = new Progress(LOGGER, "pending renames", numPending)) {
            bySuccess = pendingExecs.parallelStream().collect(
                            Collectors.groupingByConcurrent((x) -> {
                                progress.increment();
                                try {
                                    doRename(x);
                                    return true;
                                } catch (IOException e) {
                                    x.exception = e;
                                    return false;
                                }
                            }));
        }
        renames.clear();

        List<PendingFileRenamingExec> failures = bySuccess.getOrDefault(
            Boolean.FALSE, null);
        if (failures != null && failures.size() > 0) {
            numFailures = failures.size();
            double pctFailed = 100.0 * numFailures / numPending;
            String exmsg = String.format(
                "%d failures (%.1f%%) while renaming pending files",
                numFailures, pctFailed);
            throw new IOException(exmsg, failures.get(0).exception);
        }

        return numPending - numFailures;
    }

    /**
     * Attempts to delete all the tracked elements, catching any failures, and
     * throwing an exception if any failed.
     * @return the number of successful deletions
     */
    private int completeDeletions() throws IOException {
        int numPending = deletions.size();
        int numFailures = 0;

        if (numPending < 1) {
            return 0;
        }

        List<PendingFileDeletionExec> pendingExecs = deletions.
            parallelStream().map(f ->
            new PendingFileDeletionExec(f.getAbsolutePath())).collect(
            Collectors.toList());
        Map<Boolean, List<PendingFileDeletionExec>> bySuccess;

        try (Progress progress = new Progress(LOGGER, "pending deletions", numPending)) {
            bySuccess = pendingExecs.parallelStream().collect(
                            Collectors.groupingByConcurrent((x) -> {
                                progress.increment();
                                doDelete(x);
                                return true;
                            }));
        }
        deletions.clear();

        List<PendingFileDeletionExec> successes = bySuccess.getOrDefault(
            Boolean.TRUE, null);
        if (successes != null) {
            tryDeleteParents(successes);
        }

        List<PendingFileDeletionExec> failures = bySuccess.getOrDefault(
            Boolean.FALSE, null);
        if (failures != null && failures.size() > 0) {
            numFailures = failures.size();
            double pctFailed = 100.0 * numFailures / numPending;
            String exmsg = String.format(
                "%d failures (%.1f%%) while deleting pending files",
                numFailures, pctFailed);
            throw new IOException(exmsg, failures.get(0).exception);
        }

        return numPending - numFailures;
    }

    /**
     * Attempts to link the tracked elements, catching any failures, and
     * throwing an exception if any failed.
     * @return the number of successful linkages
     */
    private int completeLinkages() throws IOException {
        int numPending = linkages.size();
        int numFailures = 0;

        if (numPending < 1) {
            return 0;
        }

        List<PendingSymlinkageExec> pendingExecs =
            linkages.parallelStream().map(f ->
                new PendingSymlinkageExec(f.getSourcePath(),
                        f.getTargetRelPath())).collect(Collectors.toList());

        Map<Boolean, List<PendingSymlinkageExec>> bySuccess;
        try (Progress progress = new Progress(LOGGER, "pending linkages", numPending)) {
            bySuccess = pendingExecs.parallelStream().collect(
                            Collectors.groupingByConcurrent((x) -> {
                                progress.increment();
                                try {
                                    doLink(x);
                                    return true;
                                } catch (IOException e) {
                                    x.exception = e;
                                    return false;
                                }
                            }));
        }
        linkages.clear();

        List<PendingSymlinkageExec> failures = bySuccess.getOrDefault(
                Boolean.FALSE, null);
        if (failures != null && failures.size() > 0) {
            numFailures = failures.size();
            double pctFailed = 100.0 * numFailures / numPending;
            String exmsg = String.format(
                    "%d failures (%.1f%%) while linking pending paths",
                    numFailures, pctFailed);
            throw new IOException(exmsg, failures.get(0).exception);
        }

        return numPending - numFailures;
    }

    private void doDelete(PendingFileDeletionExec del) {
        File f = new File(TandemPath.join(del.absolutePath, PENDING_EXTENSION));
        del.absoluteParent = f.getParentFile();

        doDelete(f);
        f = new File(del.absolutePath);
        doDelete(f);
    }

    private void doDelete(File f) {
        if (f.delete()) {
            LOGGER.log(Level.FINER, "Deleted obsolete file: {0}", f.getPath());
        } else if (f.exists()) {
            LOGGER.log(Level.WARNING, "Failed to delete obsolete file: {0}",
                    f.getPath());
        }
    }

    private void doRename(PendingFileRenamingExec ren) throws IOException {
        try {
            Files.move(Paths.get(ren.source), Paths.get(ren.target),
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to move file: {0} -> {1}",
                new Object[]{ren.source, ren.target});
            throw e;
        }
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "Moved pending as file: {0}",
                ren.target);
        }
    }

    private void doLink(PendingSymlinkageExec lnk) throws IOException {
        try {
            if (!needLink(lnk)) {
                return;
            }
            Path sourcePath = Paths.get(lnk.source);
            deleteFileOrDirectory(sourcePath);

            File sourceParentFile = sourcePath.getParent().toFile();
            /*
             * The double check-exists in the following conditional is necessary
             * because during a race when two threads are simultaneously linking
             * for a not-yet-existent `sourceParentFile`, the first check-exists
             * will be false for both threads, but then only one will see true
             * from mkdirs -- so the other needs a fallback again to
             * check-exists.
             */
            if (sourceParentFile.exists() || sourceParentFile.mkdirs() ||
                    sourceParentFile.exists()) {
                Files.createSymbolicLink(sourcePath, Paths.get(lnk.targetRel));
            }
        } catch (FileAlreadyExistsException e) {
            // Another case of racing threads. Given that each of them works with the same path,
            // there is no need to worry.
            return;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to link: {0} -> {1}",
                    new Object[]{lnk.source, lnk.targetRel});
            throw e;
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "Linked pending: {0} -> {1}",
                    new Object[]{lnk.source, lnk.targetRel});
        }
    }

    private boolean needLink(PendingSymlinkageExec lnk) {
        File src = new File(lnk.source);
        Path srcpth = src.toPath();
        // needed if source doesn't exist or isn't a symlink
        if (!src.exists() || !Files.isSymbolicLink(srcpth)) {
            return true;
        }

        // Re-resolve target.
        Path tgtpth = srcpth.getParent().resolve(Paths.get(lnk.targetRel));

        // Re-canonicalize source and target.
        String srcCanonical;
        String tgtCanonical;
        try {
            srcCanonical = src.getCanonicalPath();
            tgtCanonical = tgtpth.toFile().getCanonicalPath();
        } catch (IOException ex) {
            return true;
        }
        // not needed if source's canonical matches re-resolved target canonical
        return !tgtCanonical.equals(srcCanonical);
    }

    /**
     * Deletes file or directory recursively.
     * <a href="https://stackoverflow.com/questions/779519/delete-directories-recursively-in-java">
     * Q: "Delete directories recursively in Java"
     * </a>,
     * <a href="https://stackoverflow.com/a/779529/933163">
     * A: "In Java 7+ you can use {@code Files} class."</a>,
     * <a href="https://stackoverflow.com/users/1679995/tomasz-dzi%C4%99cielewski">
     * Tomasz Dzięcielewski
     * </a>
     * @param start the starting file
     */
    private void deleteFileOrDirectory(Path start) throws IOException {
        if (!start.toFile().exists()) {
            return;
        }
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * For the unique set of parent directories among
     * {@link PendingFileDeletionExec#absoluteParent}, traverse in descending
     * order of path-length, and attempt to clean any empty directories.
     */
    private void tryDeleteParents(List<PendingFileDeletionExec> dels) {
        Set<File> parents = new TreeSet<>(DESC_PATHLEN_COMPARATOR);
        dels.forEach((del) -> parents.add(del.absoluteParent));

        SkeletonDirs skels = new SkeletonDirs();
        for (File dir : parents) {
            skels.reset();
            findFilelessChildren(skels, dir);
            skels.childDirs.forEach(this::tryDeleteDirectory);
            tryDeleteDirectory(dir);
        }
    }

    private void tryDeleteDirectory(File dir) {
        if (dir.delete()) {
            LOGGER.log(Level.FINE, "Removed empty parent dir: {0}",
                dir.getAbsolutePath());
        }
    }

    /**
     * Recursively determines eligible, file-less child directories for cleaning
     * up, and writes them to {@code skels}.
     */
    private void findFilelessChildren(SkeletonDirs skels, File directory) {
        if (!directory.exists()) {
            return;
        }
        String dirPath = directory.getAbsolutePath();
        boolean topLevelIneligible = false;
        boolean didLogFileTopLevelIneligible = false;

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(
            Paths.get(dirPath))) {
            for (Path path : directoryStream) {
                File f = path.toFile();
                if (f.isFile()) {
                    topLevelIneligible = true;
                    if (!didLogFileTopLevelIneligible && LOGGER.isLoggable(
                        Level.FINEST)) {
                        didLogFileTopLevelIneligible = true; // just once is OK
                        LOGGER.log(Level.FINEST, "not file-less due to: {0}",
                            f.getAbsolutePath());
                    }
                } else {
                    findFilelessChildren(skels, f);
                    if (!skels.ineligible) {
                        skels.childDirs.add(f);
                    } else {
                        topLevelIneligible = true;
                        if (LOGGER.isLoggable(Level.FINEST)) {
                            LOGGER.log(Level.FINEST,
                                "its children prevent delete: {0}",
                                f.getAbsolutePath());
                        }
                    }

                    // Reset this flag so that other potential, eligible
                    // children are evaluated.
                    skels.ineligible = false;
                }
            }
        } catch (IOException ex) {
            topLevelIneligible = true;
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "Failed to stream directory:" +
                    directory, ex);
            }
        }

        skels.ineligible = topLevelIneligible;
    }

    /**
     * Counts segments arising from {@code File.separatorChar} or '\\'.
     * @param path a defined instance
     * @return a natural number
     */
    private static int countPathSegments(String path) {
        int n = 1;
        for (int i = 0; i < path.length(); ++i) {
            char c = path.charAt(i);
            if (c == File.separatorChar || c == '\\') {
                ++n;
            }
        }
        return n;
    }

    private static class PendingFileDeletionExec {
        final String absolutePath;
        File absoluteParent;
        IOException exception;
        PendingFileDeletionExec(String absolutePath) {
            this.absolutePath = absolutePath;
        }
    }

    private static class PendingFileRenamingExec {
        final String source;
        final String target;
        IOException exception;
        PendingFileRenamingExec(String source, String target) {
            this.source = source;
            this.target = target;
        }
    }

    private static class PendingSymlinkageExec {
        final String source;
        final String targetRel;
        IOException exception;
        PendingSymlinkageExec(String source, String relTarget) {
            this.source = source;
            this.targetRel = relTarget;
        }
    }

    /**
     * Represents a collection of file-less directories which should also be
     * deleted for cleanliness.
     */
    private static class SkeletonDirs {
        boolean ineligible; // a flag used during recursion
        final Set<File> childDirs = new TreeSet<>(DESC_PATHLEN_COMPARATOR);

        void reset() {
            ineligible = false;
            childDirs.clear();
        }
    }
}
