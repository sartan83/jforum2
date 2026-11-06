package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_banlist</code> table.
 */
@Document(collection = BanlistDocument.COLLECTION)
public class BanlistDocument
{
	public static final String COLLECTION = "banlist";

	@Id
	private int id;

	@Indexed
	private Integer userId;

	@Indexed
	private String banlistIp;

	@Indexed
	private String banlistEmail;

	public int getId()
	{
		return this.id;
	}

	public void setId(int id)
	{
		this.id = id;
	}

	public Integer getUserId()
	{
		return this.userId;
	}

	public void setUserId(Integer userId)
	{
		this.userId = userId;
	}

	public String getBanlistIp()
	{
		return this.banlistIp;
	}

	public void setBanlistIp(String banlistIp)
	{
		this.banlistIp = banlistIp;
	}

	public String getBanlistEmail()
	{
		return this.banlistEmail;
	}

	public void setBanlistEmail(String banlistEmail)
	{
		this.banlistEmail = banlistEmail;
	}
}
