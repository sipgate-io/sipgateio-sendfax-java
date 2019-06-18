<img src="https://www.sipgatedesign.com/wp-content/uploads/wort-bildmarke_positiv_2x.jpg" alt="sipgate logo" title="sipgate" align="right" height="112" width="200"/>

# sipgate.io Java send fax example

To demonstrate how to send an Fax, we queried the `/sessions/fax` endpoint of the sipgate REST API.

For further information regarding the sipgate REST API please visit https://api.sipgate.com/v2/doc

- [Prerequisites](#Prerequisites)
- [Configuration](#Configuration)
- [How To Use](#How-To-Use)
- [How It Works](#How-It-Works)
- [Fax Extensions](#Fax-Extensions)
- [Common Issues](#Common-Issues)
- [Related](#Related)
- [Contact Us](#Contact-Us)
- [License](#License)
- [External Libraries](#External-Libraries)


## Prerequisites
- JDK 8


## Configuration
In the [application.properties](./src/resources/application.properties) file located in the project root directory insert `YOUR_SIPGATE_USERNAME`, `YOUR_SIPGATE_PASSWORD`, and `YOUR_SIPGATE_FAXLINE_ID`:

```properties
baseUrl=https://api.sipgate.com/v2
username=YOUR_SIPGATE_USERNAME
password=YOUR_SIPGATE_PASSWORD
faxlineId=YOUR_SIPGATE_FAXLINE_ID
```
The `faxlineId` uniquely identifies the extension from which you wish to send your fax. Further explanation is given in the section [Fax Extensions](#fax-extensions).


## How To Use

Run the application:

```bash
./gradlew run --args="<RECIPIENT> <PDF_DOCUMENT>"
```
**Note:** On Windows use `gradlew.bat` instead of `./gradlew`.

**Note:** Although the API accepts various formats of fax numbers the recommended format for the `RECIPIENT` is the [E.164 standard](https://en.wikipedia.org/wiki/E.164).



## How It Works

In our main application, we check that the user provides the recipient phone number and an existing PDF, we also ensure that the mime-type of the file is `application/pdf`.


```java
if(args.length < 2){
    System.err.println("Missing arguments");
    System.err.println("Please pass the recipient fax number and the file path.");
    return;
}

String recipient = args[0];

if(!recipient.matches(FAX_NUMBER_PATTERN)){
    System.err.println("Invalid recipient fax number");
    return;
}

Path pdfFilepath = Paths.get(args[1]);
if(!Files.exists(pdfFilepath)){
    System.err.println(String.format("File does not exist: %s", pdfFilepath));
    return;
}

String contentType = Files.probeContentType(pdfFilepath);

if(contentType == null || !contentType.equals("application/pdf")){
    System.err.println("Invalid file type");
    return;
}
```

After that we read the file contents, encode it with Base64, and construct a Fax object.

```java
byte[] pdfFileContent = Files.readAllBytes(pdfFilepath);
byte[] encodedPdfFileContent = Base64.getEncoder().encode(pdfFileContent);

String encodedPdf = new String(encodedPdfFileContent);

String filename = pdfFilepath.getFileName().toString();
Fax fax = new Fax(faxlineId, recipient, filename, encodedPdf);
```

After that, we call our `sendFax` method and pass in the Fax object.

```java
Optional<String> optionalSessionId = sendFax(fax);
    if(!optionalSessionId.isPresent()){
        System.err.println("Could not get session id. Please check HTTP status");
        return;
    }
    sessionId = optionalSessionId.get();
}
```

In the `sendFax` method, we define the headers and the request body, which contains the Fax object with `faxlineId`, `recipient`, `filename`, and `base64Content`.


We use the java package 'Unirest' for request generation and execution. The post method takes as argument the request URL. Headers, authorization header and the request body, are generated from header, basicAuth and body methods respectively. The request URL consists of the base URL defined above and the endpoint /sessions/fax. The method basicAuth from the 'Unirest' package takes credentials and generates the required Basic Auth header (for more information on Basic Auth see our code example).

```java
private static String sendFax(Fax fax) throws UnirestException {
    RequestBodyEntity faxRequestBodyEntity = Unirest.post(baseUrl + "/sessions/fax")
        .basicAuth(username, password)
        .header("Content-Type", "application/json")
        .body(fax);
```

Next we check if the `httpStatus` is 200, meaning that the request to send the fax was successfully received.

**Note:** Although the Api returns the status 200 it does not mean that the fax was sent. It was only added to a queue for sending.

```
int httpStatus = faxRequestBodyEntity.asString().getStatus();
System.out.println(String.format("HTTP status code: %s", httpStatus));
   
if(httpStatus != 200){
    return Optional.empty();
}
```

When the request was successful, we return an Optional of `sessionId`.

```java
FaxResponse faxResponse = faxRequestBodyEntity
        .asObject(FaxResponse.class)
        .getBody();
    return Optional.of(faxResponse.sessionId);
```

To check the status of the fax, we create a `faxStatusType` and update it in 5 second intervals using our `sessionId`. This process repeats until the fax is either sent or failed.

```java
String faxStatusType = "";
while(!faxStatusType.equals("FAILED") && !faxStatusType.equals("SENT"))
{
    try {
        faxStatusType = pollSendStatus(sessionId);
        System.out.println(faxStatusType);
    } catch (UnirestException e) {
        e.printStackTrace();
    }

    try {
        Thread.sleep(5 * 1000);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
}
```

In the `pollSendStatus` function we use Unirest again to query the `/history/{sessionId}` endpoint to get the history entry for our fax. In this case we are only interested in the `faxStatusType`.
```java
private static String pollSendStatus(String sessionId) throws UnirestException {
    JsonNode historyEntryResponse = Unirest.get(baseUrl + "/history/" + sessionId)
        .basicAuth(username, password)
        .asJson()
        .getBody();
    return historyEntryResponse.getObject().getString("faxStatusType");
}

```

The `faxStatusType` can contain the following values:
- `PENDING`: The fax was added to the queue for sending, but the sending process has not started yet
- `SENDING`: The fax is currently being sent
- `FAILED`: The fax could not be sent
- `SENT`: The fax was sent successfully
- `SCHEDULED`: The fax is scheduled for sending at the specified timestamp (it is not `PENDING` because it is not waiting in the queue of faxes to be sent yet)


## Fax Extensions

A fax extension consists of the letter 'f' followed by a number (e.g. 'f0'). The sipgate API uses the concept of fax extensions to identify devices within your account that are enabled to send fax. In this context the term 'device' does not necessarily refer to a hardware fax but rather a virtual representation.

You can find out what your extension is as follows:

1. Log into your [sipgate account](https://app.sipgate.com/w0/connections)
2. Use the sidebar to navigate to the **Connections** (_Anschlüsse_) tab
3. Click **Fax** 
4. The URL of the page should have the form `https://app.sipgate.com/{...}/connections/faxlines/{faxlineId}` where `{faxlineId}` is your Fax extension.


## Common Issues

### Fax added to the sending queue, but sending failed

Possible reasons are:

- PDF file not encoded correctly in base64
- PDF file with text fields or forms are not supported
- PDF file is corrupt

### HTTP Errors

| reason                                                                                                                                                | errorcode |
| ----------------------------------------------------------------------------------------------------------------------------------------------------- | :-------: |
| bad request (e.g. request body fields are empty or only contain spaces, timestamp is invalid etc.)                                                    |    400    |
| username and/or password are wrong                                                                                                                    |    401    |
| your account balance is insufficient                                                                                                                  |    402    |
| no permission to use specified Fax extension (e.g. Fax feature not booked or user password must be reset in [web app](https://app.sipgate.com/login)) |    403    |
| wrong REST API endpoint                                                                                                                               |    404    |
| wrong request method                                                                                                                                  |    405    |
| invalid recipient fax number                                                                                                                                  |    407    |
| wrong or missing `Content-Type` header with `application/json`                                                                                        |    415    |
| internal server error or unhandled bad request                                                                                 |    500    |


## Related

- [Unirest documentation](http://unirest.io/java.html)
- [jackson](https://github.com/FasterXML/jackson)
- [sipgate team FAQ (DE)](https://teamhelp.sipgate.de/hc/de)
- [sipgate basic FAQ (DE)](https://basicsupport.sipgate.de/hc/de)

## Contact Us

Please let us know how we can improve this example.
If you have a specific feature request or found a bug, please use **Issues** or fork this repository and send a **pull request** with your improvements.


## License

This project is licensed under **The Unlicense** (see [LICENSE file](./LICENSE)).


## External Libraries

This code uses the following external libraries

- unirest:
  - Licensed under the [MIT License](https://opensource.org/licenses/MIT)
  - Website: http://unirest.io/java.html

- jackson:
  - Licensed under the [Apache-2.0](https://opensource.org/licenses/Apache-2.0)
  - Website: https://github.com/FasterXML/jackson
  
---

[sipgate.io](https://www.sipgate.io) | [@sipgateio](https://twitter.com/sipgateio) | [API-doc](https://api.sipgate.com/v2/doc)

