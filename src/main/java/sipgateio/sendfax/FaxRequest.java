package sipgateio.sendfax;

public class FaxRequest {

	public String faxlineId;
	public String recipient;
	public String filename;
	public String base64Content;

	public FaxRequest(String faxlineId, String recipient, String filename, String base64Content) {
		this.faxlineId = faxlineId;
		this.recipient = recipient;
		this.filename = filename;
		this.base64Content = base64Content;
	}

}
