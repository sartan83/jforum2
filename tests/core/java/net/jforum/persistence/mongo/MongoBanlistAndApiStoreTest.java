package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;

import net.jforum.boot.JForumApplication;
import net.jforum.document.ApiDocument;
import net.jforum.document.BanlistDocument;
import net.jforum.entities.Banlist;
import net.jforum.persistence.ApiStore;
import net.jforum.persistence.BanlistStore;
import net.jforum.persistence.Stores;
import net.jforum.support.MongoTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoBanlistAndApiStoreTest extends MongoTestSupport
{
	@Autowired
	private BanlistStore banlist;

	@Autowired
	private ApiStore api;

	@Autowired
	private MongoTemplate mongo;

	@BeforeEach
	void clean()
	{
		this.mongo.remove(new Query(), BanlistDocument.class);
		this.mongo.remove(new Query(), ApiDocument.class);
	}

	private Banlist ban(int userId, String ip, String email)
	{
		Banlist b = new Banlist();
		b.setUserId(userId);
		b.setIp(ip);
		b.setEmail(email);
		this.banlist.insert(b);
		return b;
	}

	@Test
	void banlistInsertSelectAllDelete()
	{
		assertSame(this.banlist, Stores.banlist());

		Banlist first = this.ban(5, null, null);
		Banlist second = this.ban(0, "10.0.0.*", null);
		Banlist third = this.ban(0, null, "*@spam.com");
		assertEquals(first.getId() + 1, second.getId());

		List<Banlist> all = this.banlist.selectAll();
		assertEquals(List.of(first.getId(), second.getId(), third.getId()), all.stream().map(Banlist::getId).toList());
		assertEquals(5, all.get(0).getUserId());
		assertEquals("10.0.0.*", all.get(1).getIp());
		assertEquals("*@spam.com", all.get(2).getEmail());

		Banlist probe = new Banlist();
		probe.setIp("10.0.0.*");
		assertTrue(all.get(1).matches(probe));

		this.banlist.delete(second.getId());
		assertEquals(List.of(first.getId(), third.getId()),
			((List<Banlist>)this.banlist.selectAll()).stream().map(Banlist::getId).toList());
	}

	@Test
	void apiKeyValidation()
	{
		assertSame(this.api, Stores.api());

		ApiDocument d = new ApiDocument();
		d.setId(1);
		d.setApiKey("abc123");
		d.setApiValidity(new Date(0));
		this.mongo.insert(d);

		// ApiModel.isValid only checks that the key exists (api_validity is not evaluated)
		assertTrue(this.api.isValid("abc123"));
		assertFalse(this.api.isValid("ABC123"));
		assertFalse(this.api.isValid("nope"));
		assertFalse(this.api.isValid(""));
		assertFalse(this.api.isValid(null));
	}
}
