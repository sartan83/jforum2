package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.jforum.boot.JForumApplication;
import net.jforum.document.MailIntegrationDocument;
import net.jforum.entities.MailIntegration;
import net.jforum.persistence.MailIntegrationStore;
import net.jforum.persistence.Stores;
import net.jforum.support.MongoTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoMailIntegrationStoreTest extends MongoTestSupport
{
	@Autowired
	private MongoTemplate mongo;

	private MailIntegrationStore store;

	@BeforeEach
	void setUp()
	{
		this.store = Stores.mailintegration();
		this.mongo.remove(new Query(), MailIntegrationDocument.class);
	}

	@Test
	void crudByForumId()
	{
		this.store.add(integration(5, "five@example.com", true));
		this.store.add(integration(2, "two@example.com", false));

		List<MailIntegration> all = this.store.findAll();
		assertEquals(List.of(2, 5), all.stream().map(MailIntegration::getForumId).toList());

		MailIntegration five = this.store.find(5);
		assertEquals("five@example.com", five.getForumEmail());
		assertEquals("pop.example.com", five.getPopHost());
		assertEquals(995, five.getPopPort());
		assertEquals("user5", five.getPopUsername());
		assertEquals("secret5", five.getPopPassword());
		assertTrue(five.isSSL());
		assertFalse(this.store.find(2).isSSL());

		five.setPopHost("mail.example.com");
		five.setSSL(false);
		this.store.update(five);
		assertEquals("mail.example.com", this.store.find(5).getPopHost());
		assertFalse(this.store.find(5).isSSL());
		assertEquals(2, this.store.findAll().size());

		this.store.delete(5);
		assertNull(this.store.find(5));
		assertEquals(1, this.store.findAll().size());
	}

	static MailIntegration integration(int forumId, String email, boolean ssl)
	{
		MailIntegration mi = new MailIntegration();
		mi.setForumId(forumId);
		mi.setForumEmail(email);
		mi.setPopHost("pop.example.com");
		mi.setPopPort(ssl ? 995 : 110);
		mi.setPopUsername("user" + forumId);
		mi.setPopPassword("secret" + forumId);
		mi.setSSL(ssl);
		return mi;
	}
}
