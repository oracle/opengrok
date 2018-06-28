# REST API

Since 1.1-rc31 OpenGrok web application provides a REST API under path `/api/v1/`. Since many of these requests are meant for administrators, only requests from `localhost` are allowed (except `search` endpoint).

For Indexer and Python scripts to work correctly. URI of the webapp needs to be specified by `-U` option. (For instance: `-U http://localhost:8080/source`).

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

* **GET**
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
      "time": 387,
      "resultCount": 100,
      "startIndex": 0,
      "endIndex": 3,
      "results": [{
        "line": "java\npackage cz.cuni.mff.fruiton.",
        "path": "/fruitonserver/build/classes/java/test/cz/cuni/mff/fruiton/test/integration/MatchmakingTest.class"
      }]
    }
    ```

### `/stats`
* **GET** – returns statistics in JSON format
  
* **DELETE** – deletes statistics

* `/reload`
  * **PUT** – reloads statistics (useful when configuration path to statistics changed)

### `/system`

* `/refresh`
  * **PUT** – refreshes index searchers for specified project
    * **body** – text/plain project name to refresh
