# Release criteria

Ideally, the following minimum criteria should be fulfilled before a new
final (i.e. non-prerelease) version is released:

  - The overall code coverage must be at least 70%
  - Sonarcloud reported bugs should not have any critical issues
  - No stoppers (meaning both Issues and Pull requests)
  - All bugs and enhancements must be evaluated (for final release also go through issues with given milestone and either fix them or retarget to next release by setting the new milestone)

# Checklist for releasing OpenGrok:

The below steps are common for both pre-release and final release:

1. build must be clean

   `mvn clean package`

1. sanity check:

   - index fairly large code base, ideally multiple projects
   - deploy webapp
   - check UI:
     - history view
       - try comparing 2 distant revisions
       - check pagination
     - annotate view
     - directory listing
       - check sorting using multiple criteria
     - perform search using multiple fields across multiple projects

1. check all tests pass, test code coverage is above given threshold

   Additional tools to use: pmd, findbugs, checkstyle, jdepend

   The release is OK, once above is fulfilled to our satisfaction.

1. set new version

   `mvn versions:set -DgenerateBackupPoms=false -DnewVersion=1.1-rcXYZ`

   Then commit and push the change:

     `git commit --all`

     `git push`

1. Trigger release creation

     `git tag 1.1-rcXYZ`

     `git push origin tag 1.1-rcXYZ`

   Wait for the build to finish and release created.

   Go to https://github.com/OpenGrok/OpenGrok/releases and edit the text
   of the release, e.g. adding list of issues fixed, whether complete reindex
   is necessary etc.

1. Send announcement to opengrok-users@yahoogroups.com,
   the #opengrok Slack channel etc.