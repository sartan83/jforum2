package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_categories</code>.
 */
@Document(collection = "categories")
public class CategoryDocument
{
	@Id
	public int id;
	public String title;
	@Indexed
	public int displayOrder;
	public int moderated;
}
