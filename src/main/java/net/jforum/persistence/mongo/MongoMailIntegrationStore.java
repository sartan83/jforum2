package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.List;

import net.jforum.document.MailIntegrationDocument;
import net.jforum.document.repository.MailIntegrationDocumentRepository;
import net.jforum.entities.MailIntegration;
import net.jforum.persistence.MailIntegrationStore;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class MongoMailIntegrationStore implements MailIntegrationStore
{
	private final MailIntegrationDocumentRepository integrations;

	public MongoMailIntegrationStore(MailIntegrationDocumentRepository integrations)
	{
		this.integrations = integrations;
	}

	public void add(MailIntegration integration)
	{
		this.integrations.save(toDocument(integration));
	}

	public void update(MailIntegration integration)
	{
		this.integrations.save(toDocument(integration));
	}

	public void delete(int forumId)
	{
		this.integrations.deleteById(forumId);
	}

	public MailIntegration find(int forumId)
	{
		return this.integrations.findById(forumId).map(MongoMailIntegrationStore::toEntity).orElse(null);
	}

	public List findAll()
	{
		List<MailIntegration> l = new ArrayList<>();

		for (MailIntegrationDocument d : this.integrations.findAll(Sort.by("_id"))) {
			l.add(toEntity(d));
		}

		return l;
	}

	private static MailIntegrationDocument toDocument(MailIntegration mi)
	{
		MailIntegrationDocument d = new MailIntegrationDocument();
		d.forumId = mi.getForumId();
		d.forumEmail = mi.getForumEmail();
		d.popHost = mi.getPopHost();
		d.popUsername = mi.getPopUsername();
		d.popPassword = mi.getPopPassword();
		d.popPort = mi.getPopPort();
		d.popSsl = mi.isSSL() ? 1 : 0;
		return d;
	}

	private static MailIntegration toEntity(MailIntegrationDocument d)
	{
		MailIntegration mi = new MailIntegration();
		mi.setForumId(d.forumId);
		mi.setForumEmail(d.forumEmail);
		mi.setPopHost(d.popHost);
		mi.setPopPassword(d.popPassword);
		mi.setPopPort(d.popPort);
		mi.setPopUsername(d.popUsername);
		mi.setSSL(d.popSsl == 1);
		return mi;
	}
}
