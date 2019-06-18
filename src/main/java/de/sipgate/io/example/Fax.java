package de.sipgate.io.example;

public class Fax {

	public String faxlineId;
	public String recipient;
	public String filename;
	public String base64Content;

	public Fax(String faxlineId, String recipient, String filename, String base64Content) {
		this.faxlineId = faxlineId;
		this.recipient = recipient;
		this.filename = filename;
		this.base64Content = base64Content;
	}

}
