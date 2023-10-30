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
 * Portions Copyright (c) 2008, Peter Bray.
 * Portions Copyright (c) 2017, 2018, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import org.opengrok.indexer.configuration.CommandTimeoutType;
import org.opengrok.indexer.logger.LoggerFactory;

/**
 * Adds access to to a <a href="http://www.visible.com/Products/Razor/index.htm">Razor</a> Repository
 *
 * A brief and simplistic overview of Razor
 *
 * Razor uses the term 'Group' for what might traditionally be called a
 * repository, that is a collection of files and folders. A collection of Groups
 * is called a 'Universe' in Razor. Razor supports multiple Universes, and these
 * are the units which can be independently started and stopped.
 *
 * A universe usually consists of on issue tracking data set called "Issues",
 * this is managed from a user perspective by a GUI called 'issues' or a web
 * interface called 'issue weaver'. Each group has a file repository, managed by
 * a GUI called 'versions', and an associated GUI called 'threads' is used to
 * collect together related versions of files into a "Thread", which can be
 * thought of as a like a tag against the repository, but is managed outside of
 * the file repository itself.
 *
 * From the point of view of the user, they raise an issue to document a unit of
 * work, make changes to a collection of files against that issue, and then
 * combine one or more issues into a thread to represent a releasable product.
 * Of course, there is more to the product then this brief outline can do
 * justice to but these general concepts should assist in understanding the
 * concepts presented in the OpenGrok Razor Repository interface.
 *
 * At an implementation level, a Universe consists of it Issues database, and
 * one or more Groups each consisting of file repository and thread repository.
 * Each of these repositories is implemented with a series of directories
 * (Archive, History, Info, Scripts and Tables). When file revision control is
 * needed on a file (both committed files and some internal implementation
 * files), Razor supports the use of either SCCS or RCS for non-binary files and
 * numbered compressed instances of binary files. Each file is given a unique
 * (per universe) numerical identifier in the file
 * .../razor_db/&lt;universe&gt;/RAZOR_UNIVERSE/Mapping, this is used by Razor
 * to track files over time, as they renamed or deleted.
 *
 * Unfortunately, the Razor command line interface does not support features
 * that other SCMS support like 'log' and 'annotate'. Also, Razor check-outs
 * leave no indication that the files are from a centralized repository, so it
 * will not be possible to implement this module from a copy or check-out of the
 * repository, we will have to access (in a read-only manner) the actual
 * repository itself, extracting the information directly or via SCCS/RCS
 * interfaces.
 *
 * IMPLEMENTATION NOTES:
 *
 * The Razor implementation used for development and testing of this code has
 * the following properties which may affect the success of others trying to use
 * this implementation: - Multiple Universes - Each Universe had Issues
 * databases - Each Universe has multiple Groups, with Threads but no Projects -
 * The file repository format chosen was the non-default implementation, that
 * is, RCS rather than the default SCCS implementation - Binary files are
 * compressed with the standard UNIX 'compress' tool - Not all Groups would be
 * suitable for OpenGrok analysis - Use of Razor command line interface was
 * deemed impractical - The use of the Mapping file and the tracking of renamed
 * and deleted files was deemed too complex for the first implementation attempt
 * - The Razor implementation was on a single Sun Solaris SPARC Server - The
 * code development/testing used NetBeans-6.1 and Sun JDK 6 Update 6
 *
 * The initial implementation was to create symbolic links in the SRC_ROOT
 * directory to the Razor Group directories you wished OpenGrok to process. The
 * Razor implementation of HistoryParser and DirectoryHistoryParser were
 * functional, but the file analysis infrastructure could not support the
 * virtual filesystem that I was creating in my implementation of the
 * DirectoryHistoryParser for Razor. Essentially I was trying to make a virtual
 * file system, and remap all file names as required, but the file analysis code
 * assumed it could just read actual directories and process their contents. I
 * would have had to implement a VirtualFile and possibly VirtualFilesystem
 * classes, recode the file analysis framework and develop Standard and Razor
 * implementations. THIS APPROACH HAS BEEN ABORTED!!!
 *
 * The implementation now requires that you checkout a read-only copy of the
 * directories you wish OpenGrok to process, and place in the top-level
 * directory of each, a symlink called ".razor" to the Razor Group directory for
 * that folder. Example: if you have a universe called MyUniverse, containing a
 * group called MyGroup with top-level folders called Documentation and
 * Implementation. Then in SRC_ROOT (or a sub-directory of it), check-out
 * read-only say the Implementation into $SRC_ROOT, and create a symlink called
 * $SRC_ROOT/Implementation/.razor which points to a directory of the form
 * &lt;prefix&gt;/razor_db/&lt;Universe&gt;/RAZOR_UNIVERSE/DOMAIN_01/&lt;GroupName&gt;,
 * so that might be
 * /repository/razor/razor_db/MyUniverse/RAZOR_UNIVERSE/DOMAIN_01/MyGroup
 *
 * Because of the distributed nature of information storage in Razor (by this I
 * mean, that each file in the repository is represented by files of the same
 * name (and path) under multiple directories (Archive, History &amp; Info)),
 * I'm continuously mapping SRC_ROOT based names into the appropriate
 * subdirectory of the actual repository.
 *
 * The current implementation assumes the use of a UNIX platform, but I will try
 * not to hard-code too much in relation to these assumptions. Also I have not
 * worked Java for almost 8 years now, so please forgive any oversights in this
 * regard.
 *
 * @author Peter Bray &lt;Peter.Darren.Bray@gmail.com&gt;
 */
public class RazorRepository extends Repository {

    private static final Logger LOGGER = LoggerFactory.getLogger(RazorRepository.class);

    private static final long serialVersionUID = 1L;

    // The path of the repository itself is stored in the super class.
    // The directory containing our repository directory (usually SRC_ROOT,
    // but if the user is nesting Razor repositories in structured tree...)
    private String opengrokSourceRootDirectoryPath;
    // The base directory of that Razor Group (.razor symlink destination)
    private String razorGroupBaseDirectoryPath;

    private static final String RAZOR_DIR = ".razor";

    public RazorRepository() {
        type = "Razor";
        working = Boolean.TRUE;
        datePatterns = new String[]{
            "yyyy/MM/dd,hh:mm:ss"
        };

        ignoredDirs.add(RAZOR_DIR);
    }

    @Override
    public void setDirectoryName(File directory) {
        super.setDirectoryName(directory);
        opengrokSourceRootDirectoryPath
                = directory.getParentFile().getAbsolutePath();
        razorGroupBaseDirectoryPath
                = new File(directory, RAZOR_DIR).getAbsolutePath();
    }

    public String getOpengrokSourceRootDirectoryPath() {
        return opengrokSourceRootDirectoryPath;
    }

    public void setOpengrokSourceRootDirectoryPath(String opengrokSourceRootDirectoryPath) {
        this.opengrokSourceRootDirectoryPath = opengrokSourceRootDirectoryPath;
    }

    public String getRazorGroupBaseDirectoryPath() {
        return razorGroupBaseDirectoryPath;
    }

    public void setRazorGroupBaseDirectoryPath(String razorGroupBaseDirectoryPath) {
        this.razorGroupBaseDirectoryPath = razorGroupBaseDirectoryPath;
    }

    String getOpenGrokFileNameFor(File file) {
        return file.getAbsolutePath()
                .substring(opengrokSourceRootDirectoryPath.length());
    }

    File getSourceNameForOpenGrokName(String path) {
        return new File(opengrokSourceRootDirectoryPath + path);
    }

    File getRazorHistoryFileFor(File file) throws IOException {
        return pathTranslation(file, "/History/", "", "");
    }

    File getRazorArchiveRCSFileFor(File file) throws IOException {
        return pathTranslation(file, "/Archive/RZ_VCS/", "", ",v");
    }

    File getRazorArchiveBinaryFileFor(File file, String rev) throws IOException {
        return pathTranslation(file, "/Archive/BINARY/", "", "@" + rev + ".Z");
    }

    File getRazorArchiveSCCSFileFor(File file) throws IOException {
        return pathTranslation(file, "/Archive/SCCS/", "s.", "");
    }

    @Override
    boolean fileHasHistory(File file) {

        // @TODO : Rename & Delete Support
        try {
            File mappedFile = getRazorHistoryFileFor(file);
            return mappedFile.exists() && mappedFile.isFile();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    boolean getHistoryGet(OutputStream out, String parent, String basename, String rev) {

        // @TODO : Rename & Delete Support
        try {
            File binaryFile
                    = getRazorArchiveBinaryFileFor(new File(parent, basename), rev);
            if (binaryFile != null && binaryFile.exists()) {
                // @TODO : Implement a UNIX Compress decompression input stream
                // The standard Razor implementation uses UNIX Compress, so we
                // need to be able to decompress these files. This GZIP based
                // implementation will be useful to sites using GZIP as a
                // UNIX Compress replacement (A supported configuration
                // according to to the Razor 4.x/5.x manuals)
                try (FileInputStream in = new FileInputStream(binaryFile)) {
                    GZIPInputStream gzIn = new GZIPInputStream(in);
                    copyBytes(out::write, gzIn);
                }
                return true;
            }

            File rcsFile = getRazorArchiveRCSFileFor(new File(parent, basename));
            if (rcsFile != null && rcsFile.exists()) {
                String rcsPath = rcsFile.getPath();
                try (InputStream in = new RCSget(rcsPath, rev)) {
                    copyBytes(out::write, in);
                }
                return true;
            }

            File sccsFile = getRazorArchiveSCCSFileFor(new File(parent, basename));
            if (sccsFile != null && sccsFile.exists()) {
                ensureCommand(SCCSRepository.CMD_PROPERTY_KEY,
                        SCCSRepository.CMD_FALLBACK);
                try (InputStream in = SCCSget.getRevision(RepoCommand,
                        sccsFile, rev)) {
                    copyBytes(out::write, in);
                }
                return true;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e, () -> "getHistoryGet( "
                    + parent + ", " + basename + ", " + rev + ")");
        }
        return false;
    }

    @Override
    Annotation annotate(File file, String revision)
            throws IOException {
        // @TODO : Rename & Delete Support
        File rcsFile = getRazorArchiveRCSFileFor(file);
        if (rcsFile != null && rcsFile.exists()) {
            return (new RCSRepository()).annotate(file, revision);
        }

        File sccsFile = getRazorArchiveSCCSFileFor(file);
        if (sccsFile != null && sccsFile.exists()) {
            // @TODO : Don't create new SCCSRepositories unnecessarily
            return (new SCCSRepository()).annotate(sccsFile, revision);
        }

        return null;
    }

    @Override
    boolean fileHasAnnotation(File file) {
        // @TODO : Rename & Delete Support
        try {

            File mappedFile = getRazorArchiveRCSFileFor(file);
            if (mappedFile.exists() && mappedFile.isFile()) {
                return true;
            }

            mappedFile = getRazorArchiveSCCSFileFor(file);
            return mappedFile.exists() && mappedFile.isFile();
        } catch (Exception e) {
            return false;
        }
    }

    private File pathTranslation(File file, String intermediateElements,
            String filePrefix, String fileSuffix) throws IOException {

        File f = file;

        if (!f.getAbsolutePath().startsWith(opengrokSourceRootDirectoryPath)) {
            throw new IOException("Invalid Path for Translation '" + f.getPath()
                    + "', '" + intermediateElements + "', '" + filePrefix + "', '"
                    + fileSuffix + "'");
        }

        if (filePrefix.length() != 0) {
            f = new File(f.getParent(), filePrefix + f.getName());
        }

        StringBuilder path = new StringBuilder(razorGroupBaseDirectoryPath);
        path.append(intermediateElements);

        if (f.getAbsolutePath().length() > opengrokSourceRootDirectoryPath.length()) {
            path.append(f.getAbsolutePath()
                    .substring(opengrokSourceRootDirectoryPath.length() + 1));
        }

        if (fileSuffix.length() != 0) {
            path.append(fileSuffix);
        }

        return new File(path.toString());
    }

    @Override
    boolean isRepositoryFor(File file, CommandTimeoutType cmdType) {
        File f = new File(file, RAZOR_DIR);
        return f.exists() && f.isDirectory();
    }

    @Override
    boolean hasHistoryForDirectories() {
        return false;
    }

    @Override
    History getHistory(File file) throws HistoryException {
        return new RazorHistoryParser().parse(file, this);
    }

    @Override
    String determineParent(CommandTimeoutType cmdType) throws IOException {
        return null;
    }

    @Override
    String determineBranch(CommandTimeoutType cmdType) {
        return null;
    }

    @Override
    String determineCurrentVersion(CommandTimeoutType cmdType) throws IOException {
        return null;
    }
}
