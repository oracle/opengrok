# REST API

Since 1.1 OpenGrok web application provides a REST API under path `/api/v1/`.

For Indexer and Python scripts to work correctly. URI of the webapp needs to be specified by `-U` option. (For instance: `-U http://localhost:8080/source`).

## Authentication / Authorization
All requests to `/api` are only allowed within `localhost`. There are no authentication or authorization done for these requests.

## Endpoints
There are a few endpoints which provide different functionality.

### `/configuration`

* **GET** – returns XML representation of configuration

* **PUT** – sets configuration from XML representation
  * `?reindex=true/false` – specifies if the underlying data were also reindexed (refreshes some searchers and additional data structures)
  * **body** – XML configuration string

* `/{field}`
  * **GET** – returns specific configuration field in JSON format
  * **PUT** – sets specific configuration field `{field}`
    * **body** – string value of the field to set

* `/authorization/reload`
  * **POST** – reloads authorization framework

### `/messages`

* **POST** – adds message to a system
  * **body** – JSON representation of message, example:
    ```json
    {
      "tags": ["main"],
      "cssClass": "cssClass",
      "text":"test message",
      "duration":"PT10M"
    }
    ```

* **DELETE**
  * `?tag={t}` – deletes messages with specified tag `{t}`

* **GET** – retrieves all messages in the system
  * `?tag={t}` – returns all messages with specified tag `{t}`
  * example:
    ```json
    [
      {
        "acceptedTime": "2018-06-28T17:49:01.793Z",
        "message": {
          "tags": ["main"],
          "cssClass": "cssClass",
          "text": "test message",
          "duration": "PT10M"
        },
        "expirationTime": "2018-06-28T17:59:01.793Z",
        "expired":false
      }
    ]
    ```

### `/projects`

* **GET** – returns a list of projects

* **POST** – add project
  * **body** – text/plain name of the project

* `/{project}`
  * `**DELETE** – delete project
  * `/indexed`
    * **PUT** – marks project as indexed
  * `/property/{field}`
    * **PUT** – sets field `{field}` for the `{project}`
      * **body** – string representation of the value to set
    * **GET** – returns the `{field}` value in JSON format
  * `/repositories`
    * **GET** – returns a list of repositories for the specified `{project}` as JSON list
    * `/type`
      * **GET** – returns types of `{project}` repositories as JSON list

* `/indexed`
  * **GET** – returns a list of indexed projects as JSON list

### `/repositories`
* `/type`
  * **GET** – return type of the repository
    * `?repository={repo_name}` – repository for which to return type

### `/search`
* **GET** – return search results
  * `?full={full}` – full search field value to search for
  * `?def={def}` – definition field value to search for
  * `?sybol={symbol}` – symbol field value to search for
  * `?path={path}` – file path field value to search for
  * `?hist={hist}` – history field value to search for
  * `?type={type}` – type of the files to search for
  * `?projects={project1}&projects={project2}` – projects to search in
  * `?maxresults={max_results}` – maximum number of documents whose hits will be returned
  * `?start={start}` – start index from which to return results
  * example:
    ```json
    {
      "time": 13,
      "resultCount": 35,
      "startDocument": 0,
      "endDocument": 0,
      "results": {
        "/opengrok/test/org/opensolaris/opengrok/history/hg-export-renamed.txt": [{
          "line": "# User Vladimir <b>Kotal</b> &lt;Vladimir.<b>Kotal</b>@oracle.com&gt;",
          "lineNumber": "19"
        },{
          "line": "# User Vladimir <b>Kotal</b> &lt;Vladimir.<b>Kotal</b>@oracle.com&gt;",
          "lineNumber":"29"
        }]
      }
    }
    ```

### `/stats`
* **GET** – returns statistics in JSON format
  
* **DELETE** – deletes statistics

* `/reload`
  * **PUT** – reloads statistics (useful when configuration path to statistics changed)

### `/suggest`

* **GET** – returns suggestions
  * `?projects[]={project1}&projects[]={project2}` – list of projects for which to retrieve suggestions
  * `?field={field}` – field for which to suggest
  * `?caret={position}` – position of the caret in the input field
  * `?full={full}` – value of the `Full Search` input
  * `?defs={defs}`– value of the `Definitions` input
  * `?refs={refs}` – value of `Symbol` input
  * `?path={path}` – value of the `File Path` input
  * `?hist={hist}` – value of the `History` input
  * `?type={type}` – value of the `Type` input
  * example:
  ```json
  {
    "time": 60,
    "suggestions": [{
      "phrase": "package",
      "projects": ["kotlin"],
      "score": 387
    }],
    "identifier": "pprttq",
    "queryText": "pprttq",
    "partialResult":false
  }
  ```

* `/config`
  * **GET** – returns suggester configuration

* `/init/queries`
  * **POST** – updates popularity data based on the queries
    * **body** – JSON encoded list of queries
      * example:
      ```json
      ["http://localhost:8080/source/search?project=kotlin&q=text"]
      ```

* `/init/raw`
  * **POST** – updates popularity data based on the provided data
    * **body** – JSON encoded data
      * example:
      ```json
      [{"project":"kotlin","field":"full","token":"args","increment":100}]
      ```
* `/popularity/{project}`
  * **GET** – retrieves popularity data for `{project}`
    * `?field={field}` – field for which to retrieve data, default: `full`
    * `?page={page}` – page of data, default: `0`
    * `?pageSize={pageSize}` – size of the page, default: `100`
    * `?all={all}` – if all data should be retrieved, if `true` then `page` and `pageSize` params are ignored
### `/system`

* `/refresh`
  * **PUT** – refreshes index searchers for specified project
    * **body** – text/plain project name to refresh
* `/includes/reload`
   * **PUT** - reloads all [include files for web application](/OpenGrok/OpenGrok/wiki/Webapp-configuration#include-files)
     * **body** – empty
* `/pathdesc`
   * **POST** - updates [path descriptions for web application](/OpenGrok/OpenGrok/wiki/Webapp-configuration#path-descriptions)
     * **body** – lines of path description file