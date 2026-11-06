package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** <code>jforum_mail_integration</code>, one document per forum (<code>_id</code> = forum id). */
@Document(collection = "mail_integration")
public class MailIntegrationDocument
{
	@Id
	public int forumId;

	public String forumEmail;
	public String popUsername;
	public String popPassword;
	public String popHost;
	public int popPort = 110;
	public int popSsl;
}
