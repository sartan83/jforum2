package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** <code>jforum_extensions</code>; <code>allow</code> keeps the legacy 0/1 value. */
@Document(collection = "extensions")
public class ExtensionDocument
{
	@Id
	public int id;

	@Indexed
	public int extensionGroupId;

	public String description;
	public String uploadIcon;

	@Indexed
	public String extension;

	public int allow = 1;
}
