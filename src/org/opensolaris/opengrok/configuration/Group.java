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
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package org.opensolaris.opengrok.configuration;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Placeholder for the information about subgroups of projects and repositories.
 *
 * Supports natural ordering based on case insensitive group names.
 *
 * @author Krystof Tulinger
 * @version $Revision$
 */
public class Group implements Comparable<Group>, Nameable {

    private String name;
    /**
     * Group regexp pattern.
     *
     * No project matches the empty pattern of "" however this group can still
     * be used as a superior group for other groups (without duplicating the
     * projects).
     */
    private String pattern = "";
    /**
     * Compiled group pattern.
     *
     * We set up the empty compiled pattern by default to "()" to reduce code
     * complexity when performing a match for a group without a pattern.
     *
     * This pattern is updated whenever the string pattern {@link #pattern} is
     * updated.
     *
     * @see #setPattern(String)
     */
    private Pattern compiledPattern = Pattern.compile("()");
    private Group parent;
    private int flag;

    private Set<Group> subgroups = new TreeSet<>();
    private Set<Group> descendants = new TreeSet<>();
    private Set<Project> projects = new TreeSet<>();
    private Set<Project> repositories = new TreeSet<>();
    private Set<Group> parents;

    public Set<Project> getProjects() {
        return projects;
    }

    public void addProject(Project p) {
        this.projects.add(p);
    }

    public void addRepository(Project p) {
        this.repositories.add(p);
    }

    public Set<Group> getDescendants() {
        return descendants;
    }

    public void setDescendants(Set<Group> descendants) {
        this.descendants = descendants;
    }

    public void addDescendant(Group g) {
        this.descendants.add(g);
    }

    public void removeDescendant(Group g) {
        this.descendants.remove(g);
    }

    public Set<Project> getRepositories() {
        return repositories;
    }

    public void setSubgroups(Set<Group> subgroups) {
        this.subgroups = subgroups;
    }

    public void setProjects(Set<Project> projects) {
        this.projects = projects;
    }

    public void setRepositories(Set<Project> repositories) {
        this.repositories = repositories;
    }

    public Set<Group> getSubgroups() {
        return subgroups;
    }

    public void addGroup(Group g) {
        g.setParent(this);
        subgroups.add(g);
        descendants.add(g);
    }

    public Set<Group> getParents() {
        if (parents == null) {
            parents = new TreeSet<>();
            Group tmp = parent;
            while (tmp != null) {
                parents.add(tmp);
                tmp = tmp.getParent();
            }
        }
        return parents;
    }

    public Group getParent() {
        return parent;
    }

    public void setParent(Group parent) {
        this.parent = parent;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public String getPattern() {
        return pattern;
    }

    /**
     * Set the group pattern.
     *
     * @param pattern the regexp pattern for this group
     * @throws PatternSyntaxException when the pattern is invalid
     */
    public void setPattern(String pattern) throws PatternSyntaxException {
        this.compiledPattern = Pattern.compile("(" + pattern + ")");
        this.pattern = pattern;
    }

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    /**
     * Test group for a match
     *
     * @param p project
     * @return true if project's description matches the group pattern
     */
    public boolean match(Project p) {
        return compiledPattern.matcher(p.getName()).matches();
    }

    @Override
    public int compareTo(Group o) {
        return getName().toUpperCase(Locale.getDefault())
                .compareTo(o.getName().toUpperCase(Locale.getDefault()));
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 41 * hash + (this.name == null ? 0 : this.name.toUpperCase(Locale.getDefault()).hashCode());
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Group other = (Group) obj;
        return !(this.name != other.name
                && (this.name == null
                || !this.name.toUpperCase(Locale.getDefault()).equals(other.name.toUpperCase(Locale.getDefault()))));
    }

    /**
     * Returns group object by its name
     *
     * @param name name of a group
     * @return group that fits the name
     */
    public static Group getByName(String name) {
        Group ret = null;
        RuntimeEnvironment env = RuntimeEnvironment.getInstance();
        if (env.hasGroups()) {
            for (Group grp : env.getGroups()) {
                if (name.equals(grp.getName())) {
                    ret = grp;
                }
            }
        }
        return ret;
    }
}
