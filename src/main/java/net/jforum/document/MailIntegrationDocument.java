package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * <code>jforum_mail_integration</code>, one document per forum. The table has no primary key,
 * so <code>_id</code> is a generated sequence (as the migration tool does) and the store
 * looks documents up by <code>forumId</code>.
 */
@Document(collection = "mail_integration")
public class MailIntegrationDocument
{
	@Id
	public int id;

	@Indexed(unique = true)
	public int forumId;

	public String forumEmail;
	public String popUsername;
	public String popPassword;
	public String popHost;
	public int popPort = 110;
	public int popSsl;
}
