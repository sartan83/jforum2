package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * <code>jforum_extension_groups</code>; <code>allow</code> keeps the legacy 0/1 value,
 * <code>downloadMode</code> is 1 (inline) or 2 (physical download).
 */
@Document(collection = "extension_groups")
public class ExtensionGroupDocument
{
	@Id
	public int id;

	public String name;
	public int allow = 1;
	public String uploadIcon;
	public int downloadMode = 1;
}
