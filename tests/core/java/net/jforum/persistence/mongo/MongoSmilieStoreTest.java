package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import net.jforum.boot.JForumApplication;
import net.jforum.document.SmilieDocument;
import net.jforum.entities.Smilie;
import net.jforum.persistence.SmilieStore;
import net.jforum.persistence.Stores;
import net.jforum.support.MongoTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoSmilieStoreTest extends MongoTestSupport
{
	@Autowired
	private SmilieStore store;

	@Autowired
	private MongoTemplate mongo;

	@BeforeEach
	void setUp()
	{
		this.mongo.dropCollection(SmilieDocument.COLLECTION);
	}

	private int add(String code, String diskName)
	{
		Smilie s = new Smilie();
		s.setCode(code);
		s.setDiskName(diskName);
		s.setUrl("<img src=\"#CONTEXT#/images/smilies/" + diskName + "\" />");
		return this.store.addNew(s);
	}

	@Test
	void crudAndOrdering()
	{
		assertSame(this.store, Stores.smilie());

		int a = this.add(":)", "smile.gif");
		int b = this.add(":(", "sad.gif");
		int c = this.add(":D", "grin.gif");
		assertTrue(a < b && b < c);

		Smilie loaded = this.store.selectById(b);
		assertEquals(b, loaded.getId());
		assertEquals(":(", loaded.getCode());
		assertEquals("sad.gif", loaded.getDiskName());
		assertEquals("<img src=\"#CONTEXT#/images/smilies/sad.gif\" />", loaded.getUrl());

		loaded.setCode(":-(");
		loaded.setDiskName("sad2.gif");
		loaded.setUrl("url2");
		this.store.update(loaded);
		Smilie updated = this.store.selectById(b);
		assertEquals(":-(", updated.getCode());
		assertEquals("sad2.gif", updated.getDiskName());
		assertEquals("url2", updated.getUrl());

		List<Integer> ids = ((List<?>)this.store.selectAll()).stream()
			.map(o -> ((Smilie)o).getId()).collect(Collectors.toList());
		assertEquals(List.of(a, b, c), ids);

		this.store.delete(b);
		assertEquals(2, this.store.selectAll().size());
		assertEquals(0, this.store.selectById(b).getId());
	}
}
