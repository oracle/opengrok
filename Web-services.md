### Requirement
If you're using OpenGrok 0.12, ensure the commits in this [pull request](https://github.com/OpenGrok/OpenGrok/pull/799) are included in your OpenGrok build.

Starting from OpenGrok 1.0, the webservice is included in the provided build.

### Syntax
```bash
{host}/{webapp_name}/json?freetext={keyword}&maxresults=80...
```
{webapp_name} is the name of war archive, for example "source" in the default build configuration.

The overall query parameters (**maxresults**, **freetext**, **def**, **symbol**, **path** and **hist**) and result attributes (**directory**, **filename**, **lineno**, **line**, **path**, **results**, **duration**, **resultcount**) are defined as:

```java
private static final String PARAM_FREETEXT = "freetext"; // query params start
private static final String PARAM_DEF = "def";
private static final String PARAM_SYMBOL = "symbol";
private static final String PARAM_PATH = "path";
private static final String PARAM_HIST = "hist";
private static final String PARAM_MAXRESULTS = "maxresults"; // query params end
private static final String ATTRIBUTE_DIRECTORY = "directory"; // attributes in response JSON start
private static final String ATTRIBUTE_FILENAME = "filename";
private static final String ATTRIBUTE_LINENO = "lineno";
private static final String ATTRIBUTE_LINE = "line";
private static final String ATTRIBUTE_PATH = "path";
private static final String ATTRIBUTE_RESULTS = "results";
private static final String ATTRIBUTE_DURATION = "duration";
private static final String ATTRIBUTE_RESULT_COUNT = "resultcount"; // attributes in response JSON end
```

### Response example
You can take this response for example:
```javascript
{
  duration: 14618,
  path: "",
  resultcount: 4,
  hist: "",
  freetext: "Thread",
  results: [
    {
      path: "/my_project/my_path_section1/commonservice/biz/src/com/alipay/mobile/framework/service/common/threadpool/CommonThreadFactory.java",
      filename: "CommonThreadFactory.java",
      lineno: "18",
      line: "ICAgIHB1YmxpYyA8Yj5UaHJlYWQ8L2I+IG5ld1RocmVhZChSdW5uYWJsZSByKSB7",
      directory: "\/my_project\/my_path_section1\/commonservice\/biz\/src\/com\/alipay\/mobile\/framework\/service\/common\/threadpool"
    },
    {
      path: "/my_project/my_path_section1/commonservice/biz/src/com/alipay/mobile/framework/service/common/threadpool/CommonThreadFactory.java",
      filename: "CommonThreadFactory.java",
      lineno: "19",
      line: "ICAgICAgICA8Yj5UaHJlYWQ8L2I+IDxiPnRocmVhZDwvYj4gPSBuZXcgPGI+VGhyZWFkPC9iPihyLCB0aHJlYWROYW1lUHJlZml4",
      directory: "\/my_project\/my_path_section1\/commonservice\/biz\/src\/com\/alipay\/mobile\/framework\/service\/common\/threadpool"
    },
    {
      path: "/my_project/my_path_section1/commonservice/biz/src/com/alipay/mobile/framework/service/common/threadpool/CommonThreadFactory.java",
      filename: "CommonThreadFactory.java",
      lineno: "21",
      line: "ICAgICAgICA8Yj50aHJlYWQ8L2I+LnNldFByaW9yaXR5KHRoaXMucHJpb3JpdHkpOw==",
      directory: "\/my_project\/my_path_section1\/commonservice\/biz\/src\/com\/alipay\/mobile\/framework\/service\/common\/threadpool"
    },
    {
      path: "/my_project/my_path_section1/commonservice/biz/src/com/alipay/mobile/framework/service/common/threadpool/CommonThreadFactory.java",
      filename: "CommonThreadFactory.java",
      lineno: "22",
      line: "ICAgICAgICByZXR1cm4gPGI+dGhyZWFkPC9iPjs=",
      directory: "\/my_project\/my_path_section1\/commonservice\/biz\/src\/com\/alipay\/mobile\/framework\/service\/common\/threadpool"
    }
  ]
}
```

The attribute **line** is base 64 encoded source line with HTML attributes showing the hit position.

### Additional note
You have to add "Access-Control-Allow-Origin" header in the [JSONSearchServlet.java](https://github.com/OpenGrok/OpenGrok/blob/8319a89aaa06ff36af7fb04086caf078421086cf/src/org/opensolaris/opengrok/web/JSONSearchServlet.java) when cross-origin request is required.