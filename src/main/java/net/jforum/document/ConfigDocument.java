package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_config</code>.
 */
@Document(collection = "config")
public class ConfigDocument
{
	@Id
	public int id;
	@Indexed
	public String configName;
	public String configValue;
}
