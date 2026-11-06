package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.jforum.boot.JForumApplication;
import net.jforum.document.RankDocument;
import net.jforum.entities.Ranking;
import net.jforum.persistence.RankingStore;
import net.jforum.persistence.Stores;
import net.jforum.support.MongoTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoRankingStoreTest extends MongoTestSupport
{
	@Autowired
	private RankingStore ranks;

	@Autowired
	private MongoTemplate mongo;

	@BeforeEach
	void clean()
	{
		this.mongo.remove(new Query(), RankDocument.class);
	}

	private Ranking add(String title, int min, boolean special)
	{
		Ranking r = new Ranking();
		r.setTitle(title);
		r.setMin(min);
		r.setSpecial(special);
		r.setImage(title + ".gif");
		this.ranks.addNew(r);
		return r;
	}

	private static List<String> titles(List l)
	{
		return ((List<Ranking>)l).stream().map(Ranking::getTitle).toList();
	}

	@Test
	void crudOrderingAndSpecials()
	{
		assertSame(this.ranks, Stores.ranking());

		Ranking master = this.add("Master", 100, false);
		Ranking newbie = this.add("Newbie", 0, false);
		Ranking admin = this.add("Admin", 0, true);
		this.add("Regular", 10, false);
		assertEquals(master.getId() + 1, newbie.getId());

		// RankingModel.selectAll: ORDER BY rank_min
		assertEquals(List.of("Newbie", "Admin", "Regular", "Master"), titles(this.ranks.selectAll()));
		assertEquals(List.of("Admin"), titles(this.ranks.selectSpecials()));

		Ranking loaded = this.ranks.selectById(admin.getId());
		assertEquals("Admin", loaded.getTitle());
		assertEquals("Admin.gif", loaded.getImage());
		assertTrue(loaded.isSpecial());
		assertEquals(0, this.ranks.selectById(9999).getId());

		loaded.setTitle("Staff");
		loaded.setSpecial(false);
		loaded.setMin(50);
		loaded.setImage("staff.gif");
		this.ranks.update(loaded);
		Ranking updated = this.ranks.selectById(admin.getId());
		assertEquals("Staff", updated.getTitle());
		assertFalse(updated.isSpecial());
		assertEquals(50, updated.getMin());
		assertEquals("staff.gif", updated.getImage());
		assertTrue(this.ranks.selectSpecials().isEmpty());

		this.ranks.delete(newbie.getId());
		assertEquals(List.of("Regular", "Staff", "Master"), titles(this.ranks.selectAll()));
	}
}
