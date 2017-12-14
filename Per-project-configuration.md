# Table of contents

<!-- toc -->

- [Per project properties](#per-project-properties)
  * [Setup](#setup)
  * [List of tunables](#list-of-tunables)

# Per project properties

## Setup

```
  <void property="projects">
   <void method="put">
    <string>PROJECT_NAME</string>
    <object class="org.opensolaris.opengrok.configuration.Project">
     <void property="navigateWindowEnabled">
      <boolean>true</boolean>
     </void>
    </object>
   </void>
  </void>
```

## List of tunables

- `navigateWindowEnabled` : enable navigate window when browsing xrefs