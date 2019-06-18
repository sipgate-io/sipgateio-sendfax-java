package de.sipgate.io.example;

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

public class App {

	private static final Properties properties = new Properties();

	private static String baseUrl;
	private static String username;
	private static String password;
	private static String faxlineId;
	private static final String FAX_NUMBER_PATTERN = "\\+?[0-9]+";

	public static void main(String[] args) throws IOException {
		Unirest.setObjectMapper(new ResponseMapper());

		loadConfiguration();

		if (args.length < 2) {
			System.err.println("Missing arguments");
			System.err.println("Please pass the recipient fax number and the file path.");
			return;
		}

		String recipient = args[0];

		if (!recipient.matches(FAX_NUMBER_PATTERN)) {
			System.err.println("Invalid recipient fax number");
			return;
		}

		Path pdfFilepath = Paths.get(args[1]);
		if (!Files.exists(pdfFilepath)) {
			System.err.println(String.format("File does not exist: %s", pdfFilepath));
			return;
		}

		String contentType = Files.probeContentType(pdfFilepath);

		if (contentType == null || !contentType.equals("application/pdf")) {
			System.err.println("Invalid file type");
			return;
		}

		byte[] pdfFileContent = Files.readAllBytes(pdfFilepath);
		byte[] encodedPdfFileContent = Base64.getEncoder().encode(pdfFileContent);

		String encodedPdf = new String(encodedPdfFileContent);

		String filename = pdfFilepath.getFileName().toString();
		Fax fax = new Fax(faxlineId, recipient, filename, encodedPdf);

		String sessionId;
		try {
			Optional<String> optionalSessionId = sendFax(fax);
			if (!optionalSessionId.isPresent()) {
				System.err.println("Could not get session id. Please check HTTP status");
				return;
			}
			sessionId = optionalSessionId.get();

		} catch (UnirestException e) {
			e.printStackTrace();
			return;
		}

		String faxStatusType = "";
		while (!faxStatusType.equals("FAILED") && !faxStatusType.equals("SENT")) {
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

	}

	private static void loadConfiguration() {
		try {
			properties.load(App.class.getClassLoader().getResourceAsStream("application.properties"));
		} catch (IOException e) {
			System.err.println("Could not load file application.properties");
		}

		baseUrl = properties.getProperty("baseUrl");
		username = properties.getProperty("username");
		password = properties.getProperty("password");
		faxlineId = properties.getProperty("faxlineId");
	}

	private static Optional<String> sendFax(Fax fax) throws UnirestException {
		RequestBodyEntity faxRequestBodyEntity = Unirest.post(baseUrl + "/sessions/fax")
				.basicAuth(username, password)
				.header("Content-Type", "application/json")
				.body(fax);

		int httpStatus = faxRequestBodyEntity.asString().getStatus();
		System.out.println(String.format("HTTP status code: %s", httpStatus));

		if (httpStatus != 200) {
			return Optional.empty();
		}

		try {
			FaxResponse faxResponse = faxRequestBodyEntity
					.asObject(FaxResponse.class)
					.getBody();

			return Optional.of(faxResponse.sessionId);
		} catch (UnirestException e) {
			e.printStackTrace();
		}

		return null;
	}

	private static String pollSendStatus(String sessionId) throws UnirestException {
		JsonNode historyEntryResponse = Unirest.get(baseUrl + "/history/" + sessionId)
				.basicAuth(username, password)
				.asJson()
				.getBody();
		return historyEntryResponse.getObject().getString("faxStatusType");
	}

}
