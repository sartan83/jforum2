package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import net.jforum.boot.JForumApplication;
import net.jforum.document.BannerDocument;
import net.jforum.entities.Banner;
import net.jforum.persistence.BannerStore;
import net.jforum.persistence.Stores;
import net.jforum.support.MongoTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoBannerStoreTest extends MongoTestSupport
{
	@Autowired
	private BannerStore store;

	@Autowired
	private MongoTemplate mongo;

	@BeforeEach
	void setUp()
	{
		this.mongo.dropCollection(BannerDocument.COLLECTION);
	}

	private Banner banner(String name, int placement, int weight, boolean active, String comment)
	{
		Banner b = new Banner();
		b.setName(name);
		b.setPlacement(placement);
		b.setWeight(weight);
		b.setActive(active);
		b.setComment(comment);
		b.setDescription("<img src='" + name + "'/>");
		b.setUrl("http://example.com/" + name);
		b.setType(1);
		b.setWidth(468);
		b.setHeight(60);
		b.setClicks(3);
		b.setViews(7);
		this.store.addNew(b);
		return b;
	}

	private static List<String> names(List<?> l)
	{
		return l.stream().map(o -> ((Banner)o).getName()).collect(Collectors.toList());
	}

	@Test
	void crud()
	{
		assertSame(this.store, Stores.banner());

		Banner b = this.banner("top", 1, 5, true, "c");
		assertTrue(b.getId() > 0);

		Banner loaded = this.store.selectById(b.getId());
		assertEquals("top", loaded.getName());
		assertEquals(1, loaded.getPlacement());
		assertEquals(5, loaded.getWeight());
		assertTrue(loaded.isActive());
		assertEquals("c", loaded.getComment());
		assertEquals("<img src='top'/>", loaded.getDescription());
		assertEquals("http://example.com/top", loaded.getUrl());
		assertEquals(1, loaded.getType());
		assertEquals(468, loaded.getWidth());
		assertEquals(60, loaded.getHeight());
		assertEquals(3, loaded.getClicks());
		assertEquals(7, loaded.getViews());

		// BannerCommon increments the views and saves through update()
		loaded.setViews(loaded.getViews() + 1);
		loaded.setActive(false);
		loaded.setName("renamed");
		this.store.update(loaded);
		Banner updated = this.store.selectById(b.getId());
		assertEquals(8, updated.getViews());
		assertFalse(updated.isActive());
		assertEquals("renamed", updated.getName());

		Banner ghost = new Banner(9999);
		this.store.update(ghost);
		assertFalse(this.store.canDelete(9999));
		assertEquals(0, this.store.selectById(9999).getId());

		assertTrue(this.store.canDelete(b.getId()));
		this.store.delete(b.getId());
		assertFalse(this.store.canDelete(b.getId()));
		assertTrue(this.store.selectAll().isEmpty());
	}

	@Test
	void selectAllIsOrderedByComment()
	{
		this.banner("x", 0, 1, true, "b");
		this.banner("y", 0, 1, true, "a");
		this.banner("z", 0, 1, true, "c");

		assertEquals(List.of("y", "x", "z"), names(this.store.selectAll()));
	}

	@Test
	void activeBannersByPlacementOrderedByWeight()
	{
		this.banner("heavy", 1, 9, true, "");
		this.banner("light", 1, 1, true, "");
		this.banner("inactive", 1, 0, false, "");
		this.banner("elsewhere", 2, 0, true, "");
		this.banner("middle", 1, 5, true, "");

		assertEquals(List.of("light", "middle", "heavy"), names(this.store.selectActiveBannerByPlacement(1)));
		assertEquals(List.of("elsewhere"), names(this.store.selectActiveBannerByPlacement(2)));
		assertTrue(this.store.selectActiveBannerByPlacement(3).isEmpty());
	}
}
