package net.jforum.document;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_sessions</code> table, keyed by <code>session_user_id</code>.
 */
@Document(collection = SessionDocument.COLLECTION)
public class SessionDocument
{
	public static final String COLLECTION = "sessions";

	@Id
	private int id;
	private String sessionId;
	private Date sessionStart;
	private Long sessionTime;
	private String sessionIp;
	private Integer sessionPage;
	private Integer sessionLoggedInt;

	public int getId()
	{
		return this.id;
	}

	public void setId(int id)
	{
		this.id = id;
	}

	public String getSessionId()
	{
		return this.sessionId;
	}

	public void setSessionId(String sessionId)
	{
		this.sessionId = sessionId;
	}

	public Date getSessionStart()
	{
		return this.sessionStart;
	}

	public void setSessionStart(Date sessionStart)
	{
		this.sessionStart = sessionStart;
	}

	public Long getSessionTime()
	{
		return this.sessionTime;
	}

	public void setSessionTime(Long sessionTime)
	{
		this.sessionTime = sessionTime;
	}

	public String getSessionIp()
	{
		return this.sessionIp;
	}

	public void setSessionIp(String sessionIp)
	{
		this.sessionIp = sessionIp;
	}

	public Integer getSessionPage()
	{
		return this.sessionPage;
	}

	public void setSessionPage(Integer sessionPage)
	{
		this.sessionPage = sessionPage;
	}

	public Integer getSessionLoggedInt()
	{
		return this.sessionLoggedInt;
	}

	public void setSessionLoggedInt(Integer sessionLoggedInt)
	{
		this.sessionLoggedInt = sessionLoggedInt;
	}
}
