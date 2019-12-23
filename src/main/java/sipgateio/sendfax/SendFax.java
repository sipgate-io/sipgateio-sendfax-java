package sipgateio.sendfax;

import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.body.RequestBodyEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Optional;
import java.util.Properties;

public class SendFax {

	private static final Properties properties = new Properties();

	private static String baseUrl;
	private static String username;
	private static String password;
	private static String faxlineId;

	private static final String FAX_NUMBER_PATTERN = "\\+?[0-9]+";

	public static void main(String[] args) {

		try {
			loadConfiguration();
		} catch (IOException e) {
			System.err.println("Could not load file application.properties");
			return;
		}

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

		String filename = pdfFilepath.getFileName().toString();
		FaxRequest faxRequest = new FaxRequest(faxlineId, recipient, filename, encodedPdf);

		Unirest.setObjectMapper(new ResponseMapper());

		String sessionId;
		try {
			sessionId = sendFax(faxRequest);
		} catch (UnirestException e) {
			System.err.println(String.format("Fax request failed: %s", e.getMessage()));
			return;
		}

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

	}

	private static String encodePdf(Path pdfFilepath) throws IOException, IllegalArgumentException {
		String contentType = Files.probeContentType(pdfFilepath);
		if (contentType == null || !contentType.equals("application/pdf")) {
			throw new IllegalArgumentException("Not a valid pdf file");
		}

		byte[] pdfFileContent = Files.readAllBytes(pdfFilepath);
		byte[] encodedPdfFileContent = Base64.getEncoder().encode(pdfFileContent);

		return new String(encodedPdfFileContent);
	}

	private static void loadConfiguration() throws IOException {
		properties.load(SendFax.class.getClassLoader().getResourceAsStream("application.properties"));

		baseUrl = properties.getProperty("baseUrl");
		username = properties.getProperty("username");
		password = properties.getProperty("password");
		faxlineId = properties.getProperty("faxlineId");
	}

	private static String sendFax(FaxRequest faxRequest) throws UnirestException {
		RequestBodyEntity response = Unirest.post(baseUrl + "/sessions/fax")
				.basicAuth(username, password)
				.header("Content-Type", "application/json")
				.body(faxRequest);

		int httpStatus = response.asString()
				.getStatus();

		if (httpStatus != 200) {
			throw new UnirestException(String.format("Server responded with error code %s", httpStatus));
		}

		FaxResponse faxResponseBody = response.asObject(FaxResponse.class)
					.getBody();

		return faxResponseBody.sessionId;
	}

	private static String pollSendStatus(String sessionId) throws UnirestException {
		JsonNode historyEntryResponse = Unirest.get(baseUrl + "/history/" + sessionId)
				.basicAuth(username, password)
				.asJson()
				.getBody();
		return historyEntryResponse.getObject().getString("faxStatusType");
	}

}
