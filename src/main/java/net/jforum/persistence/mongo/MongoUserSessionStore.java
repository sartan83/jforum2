package net.jforum.persistence.mongo;

import net.jforum.document.SessionDocument;
import net.jforum.document.repository.SessionDocumentRepository;
import net.jforum.entities.UserSession;
import net.jforum.persistence.UserSessionStore;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * MongoDB implementation of {@link UserSessionStore} (former <code>GenericUserSessionDAO</code>).
 * One document per user (<code>_id</code> = <code>session_user_id</code>).
 */
@Component
public class MongoUserSessionStore implements UserSessionStore
{
	private final MongoTemplate mongo;
	private final SessionDocumentRepository sessions;

	public MongoUserSessionStore(MongoTemplate mongo, SessionDocumentRepository sessions)
	{
		this.mongo = mongo;
		this.sessions = sessions;
	}

	public void add(UserSession us)
	{
		if (this.selectById(us) != null) {
			return;
		}

		this.insert(us);
	}

	private void insert(UserSession us)
	{
		SessionDocument d = new SessionDocument();
		d.setId(us.getUserId());
		d.setSessionId(us.getSessionId());
		d.setSessionStart(us.getStartTime());
		d.setSessionTime(0L);
		d.setSessionIp("");
		d.setSessionPage(0);
		this.sessions.insert(d);
	}

	public void update(UserSession us)
	{
		if (this.selectById(us) == null) {
			this.insert(us);
			return;
		}

		this.mongo.updateFirst(new Query(Criteria.where("_id").is(us.getUserId())),
			new Update().set("sessionStart", us.getStartTime())
				.set("sessionTime", us.getSessionTime())
				.set("sessionId", us.getSessionId()),
			SessionDocument.class);
	}

	public UserSession selectById(UserSession us)
	{
		SessionDocument d = this.sessions.findById(us.getUserId()).orElse(null);

		if (d == null) {
			return null;
		}

		UserSession returnUs = new UserSession(us);
		returnUs.setSessionTime(d.getSessionTime() == null ? 0 : d.getSessionTime());
		returnUs.setStartTime(d.getSessionStart());

		return returnUs;
	}
}
