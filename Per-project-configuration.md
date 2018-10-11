# Table of contents

<!-- toc -->

- [Per project properties](#per-project-properties)
  * [Setup](#setup)
  * [List of tunables](#list-of-tunables)

# Per project properties

Each project can have its own set of properties. These control various aspects of how the project (plus its repositories) is handled.

## Setup

The setting of per-project tunables is done in read-only configuration file (see https://github.com/OpenGrok/OpenGrok/wiki/Read-only-configuration). The file can look like this:

```
<?xml version="1.0" encoding="UTF-8"?>
<java version="1.8.0_121" class="java.beans.XMLDecoder">
 <object class="org.opengrok.indexer.configuration.Configuration" id="Configuration0">

  ...

  <void property="projects">
   <void method="put">
    <string>PROJECT_NAME</string>
    <object class="org.opengrok.indexer.configuration.Project">
     <void property="navigateWindowEnabled">
      <boolean>true</boolean>
     </void>
    </object>
   </void>
  </void>

 </object>
</java>
```

## Tunables

Tunable | Type | Since version | Meaning
--------|------|---------------|--------
`navigateWindowEnabled` | boolean | 1.1 | display navigate window automatically when browsing xrefs
`tabSize` | integer | pre 0.12 | size of tabulator in spaces
`handleRenamedFiles` | boolean | 1.1 | disables or enables handling of renamed files
`historyEnabled` | boolean | 1.1 | enables or disables history for particular project