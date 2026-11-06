package net.jforum.document;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_api</code> table.
 */
@Document(collection = ApiDocument.COLLECTION)
public class ApiDocument
{
	public static final String COLLECTION = "api";

	@Id
	private int id;

	@Indexed(unique = true)
	private String apiKey;
	private Date apiValidity;

	public int getId()
	{
		return this.id;
	}

	public void setId(int id)
	{
		this.id = id;
	}

	public String getApiKey()
	{
		return this.apiKey;
	}

	public void setApiKey(String apiKey)
	{
		this.apiKey = apiKey;
	}

	public Date getApiValidity()
	{
		return this.apiValidity;
	}

	public void setApiValidity(Date apiValidity)
	{
		this.apiValidity = apiValidity;
	}
}
