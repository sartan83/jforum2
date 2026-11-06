package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.jforum.entities.Category;
import net.jforum.entities.Config;
import net.jforum.persistence.Stores;

import org.junit.jupiter.api.Test;

class MongoCategoryAndConfigStoreTest extends ForumCoreTestSupport
{
	@Test
	void storesAreRegisteredInTheLocator()
	{
		assertEquals(this.categories, Stores.category());
		assertEquals(this.forums, Stores.forum());
		assertEquals(this.topics, Stores.topic());
		assertEquals(this.posts, Stores.post());
		assertEquals(this.polls, Stores.poll());
		assertEquals(this.configs, Stores.config());
	}

	@Test
	void categoryCrudAndOrdering()
	{
		int a = this.category("A");
		int b = this.category("B");
		int c = this.category("C");

		List<Category> all = this.categories.selectAll();
		assertEquals(List.of("A", "B", "C"), all.stream().map(Category::getName).toList());
		assertEquals(List.of(1, 2, 3), all.stream().map(Category::getOrder).toList());

		Category cb = this.categories.selectById(b);
		cb.setName("B2");
		cb.setModerated(true);
		this.categories.update(cb);
		assertEquals("B2", this.categories.selectById(b).getName());
		assertTrue(this.categories.selectById(b).isModerated());

		this.categories.setOrderUp(this.categories.selectById(c), this.categories.selectById(b));
		assertEquals(List.of(a, c, b), ids(this.categories.selectAll()));

		this.categories.setOrderDown(this.categories.selectById(a), this.categories.selectById(c));
		assertEquals(List.of(c, a, b), ids(this.categories.selectAll()));

		this.forum(a, "forum");
		assertFalse(this.categories.canDelete(a));
		assertTrue(this.categories.canDelete(b));

		this.categories.delete(b);
		assertEquals(0, this.categories.selectById(b).getId());
		assertEquals(2, this.categories.selectAll().size());
	}

	@Test
	void configCrud()
	{
		Config c = new Config();
		c.setName("forum.title");
		c.setValue("JForum");
		this.configs.insert(c);

		Config other = new Config();
		other.setName("posts.per.page");
		other.setValue("15");
		this.configs.insert(other);

		assertTrue(c.getId() > 0);
		assertEquals("JForum", this.configs.selectByName("forum.title").getValue());
		assertEquals(List.of("forum.title", "posts.per.page"),
			((List<Config>)this.configs.selectAll()).stream().map(Config::getName).toList());

		c.setValue("JForum Mongo");
		this.configs.update(c);
		assertEquals("JForum Mongo", this.configs.selectByName("forum.title").getValue());

		this.configs.delete(c);
		assertNull(this.configs.selectByName("forum.title"));
		assertEquals(1, this.configs.selectAll().size());
	}

	private static List<Integer> ids(List<Category> l)
	{
		return l.stream().map(Category::getId).toList();
	}
}
