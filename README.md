<img src="https://www.sipgatedesign.com/wp-content/uploads/wort-bildmarke_positiv_2x.jpg" alt="sipgate logo" title="sipgate" align="right" height="112" width="200"/>

# sipgate.io Java send fax example

This example demonstrates how to send a fax using the sipgate REST API.

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

In the [application.properties](./src/resources/application.properties) file located in the project root directory replace `YOUR_SIPGATE_TOKEN_ID`, `YOUR_SIPGATE_TOKEN`, and `YOUR_SIPGATE_FAXLINE_ID` with the respective values:

```properties
baseUrl=https://api.sipgate.com/v2
tokenId=YOUR_SIPGATE_TOKEN_ID
token=YOUR_SIPGATE_TOKEN
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

In `SendFax.main()` we check that the user provides the recipient phone number and an existing PDF:


```java
private static final String FAX_NUMBER_PATTERN = "\\+?[0-9]+";

public static void main(String[] args) {
	...
	if (args.length < 2) {
		System.err.println("Missing arguments");
		System.err.println("Please pass the recipient faxRequest number and the file path.");
		return;
	}

	String recipient = args[0];
	if (!recipient.matches(FAX_NUMBER_PATTERN)) {
		System.err.println("Invalid recipient faxRequest number");
		return;
	}
	
	Path pdfFilepath = Paths.get(args[1]);
	if (!Files.exists(pdfFilepath)) {
		System.err.println(String.format("File does not exist: %s", pdfFilepath));
		return;
	}
	
	String encodedPdf;
	try {
		encodedPdf = encodePdf(pdfFilepath);
	} catch (IOException | IllegalArgumentException e) {
		System.err.println(String.format("Failed to encode file %s: %s", pdfFilepath, e.getMessage()));
		return;
	}
	...
}
```

After that we ensure that the mime-type of the file is `application/pdf`, read the file contents, and encode it with `Base64`

```java
private static String encodePdf(Path pdfFilepath) throws IOException, IllegalArgumentException {
	String contentType = Files.probeContentType(pdfFilepath);
	if (contentType == null || !contentType.equals("application/pdf")) {
		throw new IllegalArgumentException("Not a valid pdf file");
	}

	byte[] pdfFileContent = Files.readAllBytes(pdfFilepath);
	byte[] encodedPdfFileContent = Base64.getEncoder().encode(pdfFileContent);

	return new String(encodedPdfFileContent);
}
```

Then we construct a `FaxRequest` object:

```java
String filename = pdfFilepath.getFileName().toString();
Fax fax = new Fax(faxlineId, recipient, filename, encodedPdf);
```
We pass Unirest a `ResponseMapper` for mapping responses in JSON format to Java objects:

```java
Unirest.setObjectMapper(new ResponseMapper());
```

After that, we call our `sendFax` method with the `FaxRequest` object as a parameter. The return value is the `sessionId`, which we will later use to track the sending status.

```java
String sessionId;
try {
  sessionId = sendFax(faxRequest);
} catch (UnirestException e) {
  System.err.println(String.format("Fax request failed: %s", e.getMessage()));
  return;
}
```

In the `sendFax` method, we define the headers and the request body, which contains the FaxRequest object with `faxlineId`, `recipient`, `filename`, and `base64Content`.


We use the _Unirest_ library for request generation and execution. The `post()` method takes as argument the request URL. The headers, authorization header, and request body are set by the `header()`, `basicAuth()` and `body()` method, respectively.
The request URL consists of the base URL defined above and the endpoint `/sessions/fax`.
The `basicAuth()` method from the _Unirest_ package takes credentials and generates the required Basic Auth header (for more information on Basic Auth [see our code example](https://github.com/sipgate-io/sipgateio-basicauth-java)).

```java
private static String sendFax(FaxRequest faxRequest) throws UnirestException {
	RequestBodyEntity response = Unirest.post(baseUrl + "/sessions/fax")
	.basicAuth(tokenId, token)
	.header("Content-Type", "application/json")
	.body(faxRequest);
```

Next we check if the `httpStatus` is 200 indicating that the request to send the fax was received successfully.

```java
int httpStatus = response.asString()
	.getStatus();

if (httpStatus != 200) {
	throw new UnirestException(String.format("Server responded with error code %s", httpStatus));
}
```
**Note:** Although the API returns the status 200 it does not mean that the fax was sent, only that it has been queued for sending.

If the request was successful, we map the response to a `FaxResponse` object and return its `sessionId`:

```java
FaxResponse faxResponseBody = response.asObject(FaxResponse.class)
					.getBody();

return faxResponseBody.sessionId;
```

To check the status of the fax, we update the `faxStatusType` in 5 second intervals using our `sessionId`. This process repeats until the `faxStatusType` is either `"SENT"` or `"FAILED"`.

```java
String faxStatusType = "";
do {
	try {
		faxStatusType = pollSendStatus(sessionId);
		System.out.println(faxStatusType);
		Thread.sleep(5 * 1000);
	} catch (InterruptedException | UnirestException e) {
		e.printStackTrace();
		return;
	}
} while (!faxStatusType.equals("FAILED") && !faxStatusType.equals("SENT"));
```

In the `pollSendStatus` function we make a GET request to the `/history/{sessionId}` endpoint which yields the history entry that corresponds to our fax.
In this case we are only interested in the `faxStatusType`.

```java
private static String pollSendStatus(String sessionId) throws UnirestException {
    JsonNode historyEntryResponse = Unirest.get(baseUrl + "/history/" + sessionId)
        .basicAuth(tokenId, token)
        .asJson()
        .getBody();
    return historyEntryResponse.getObject().getString("faxStatusType");
}
```

The `faxStatusType` can be one of the following values:
- `PENDING`: The fax was added to the queue for sending, but the sending process has not started yet
- `SENDING`: The fax is currently being sent
- `FAILED`: The fax could not be sent
- `SENT`: The fax was sent successfully
- `SCHEDULED`: The fax is scheduled for sending at the specified timestamp (it is not `PENDING` because it is not waiting in the queue of faxes to be sent yet)


## Fax Extensions

A fax extension consists of the letter `f` followed by a number (e.g. `f0`). The sipgate API uses the concept of fax extensions to identify devices within your account that are enabled to send fax. In this context the term _device_ does not necessarily refer to a hardware fax but rather a virtual representation.

You can find out what your extension is as follows:

1. Log into your [sipgate account](https://app.sipgate.com/w0/connections)
2. Use the sidebar to navigate to the **Connections** (_Anschlüsse_) tab
3. Click **Fax** 
4. The URL of the page should have the form `https://app.sipgate.com/{...}/connections/faxlines/{faxlineId}` where `{faxlineId}` is your fax extension.


## Common Issues

### Fax added to the sending queue, but sending failed

Possible reasons are:

- PDF file not encoded correctly in base64
- PDF file with text fields or forms are not supported
- PDF file is corrupted

### HTTP Errors

| reason                                                                                                                                                | errorcode |
| ----------------------------------------------------------------------------------------------------------------------------------------------------- | :-------: |
| bad request (e.g. request body fields are empty or only contain spaces, timestamp is invalid etc.)                                                    |    400    |
| tokenId and/or token are wrong                                                                                                                        |    401    |
| your account balance is insufficient                                                                                                                  |    402    |
| no permission to use specified Fax extension (e.g. Fax feature not booked or user password must be reset in [web app](https://app.sipgate.com/login)) |    403    |
| wrong REST API endpoint                                                                                                                               |    404    |
| wrong request method                                                                                                                                  |    405    |
| invalid recipient fax number                                                                                                                                  |    407    |
| wrong or missing `Content-Type` header with `application/json`                                                                                        |    415    |
| internal server error or unhandled bad request                                                                                 |    500    |


## Related

- [Unirest documentation](http://unirest.io/java.html)
- [Jackson](https://github.com/FasterXML/jackson)
- [sipgate team FAQ (DE)](https://teamhelp.sipgate.de/hc/de)
- [sipgate basic FAQ (DE)](https://basicsupport.sipgate.de/hc/de)

## Contact Us

Please let us know how we can improve this example.
If you have a specific feature request or found a bug, please use **Issues** or fork this repository and send a **pull request** with your improvements.


## License

This project is licensed under **The Unlicense** (see [LICENSE file](./LICENSE)).


## External Libraries

This code uses the following external libraries

- _Unirest_:
  - Licensed under the [MIT License](https://opensource.org/licenses/MIT)
  - Website: http://unirest.io/java.html

- _Jackson_:
  - Licensed under the [Apache-2.0](https://opensource.org/licenses/Apache-2.0)
  - Website: https://github.com/FasterXML/jackson
  
---

[sipgate.io](https://www.sipgate.io) | [@sipgateio](https://twitter.com/sipgateio) | [API-doc](https://api.sipgate.com/v2/doc)

