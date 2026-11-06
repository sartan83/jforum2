package net.jforum.document;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A SystemGlobals override saved from the admin "Configurations" panel
 * (former <code>installation.config</code> file). <code>_id</code> is the SystemGlobals key.
 */
@Document(collection = SettingDocument.COLLECTION)
public class SettingDocument
{
	public static final String COLLECTION = "settings";

	@Id
	private String key;
	private String value;
	private Date updatedAt;

	public SettingDocument() {}

	public SettingDocument(String key, String value, Date updatedAt)
	{
		this.key = key;
		this.value = value;
		this.updatedAt = updatedAt;
	}

	public String getKey() { return this.key; }
	public void setKey(String key) { this.key = key; }
	public String getValue() { return this.value; }
	public void setValue(String value) { this.value = value; }
	public Date getUpdatedAt() { return this.updatedAt; }
	public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
