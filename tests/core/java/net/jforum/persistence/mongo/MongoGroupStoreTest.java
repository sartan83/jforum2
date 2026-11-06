package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.jforum.boot.JForumApplication;
import net.jforum.document.GroupDocument;
import net.jforum.document.RoleDocument;
import net.jforum.document.UserDocument;
import net.jforum.entities.Group;
import net.jforum.persistence.GroupStore;
import net.jforum.persistence.Stores;
import net.jforum.persistence.TreeGroupStore;
import net.jforum.security.Role;
import net.jforum.support.MongoTestSupport;
import net.jforum.util.GroupNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoGroupStoreTest extends MongoTestSupport
{
	@Autowired
	private GroupStore groups;

	@Autowired
	private TreeGroupStore tree;

	@Autowired
	private MongoTemplate mongo;

	@BeforeEach
	void clean()
	{
		this.mongo.remove(new Query(), GroupDocument.class);
		this.mongo.remove(new Query(), RoleDocument.class);
		this.mongo.remove(new Query(), UserDocument.class);
	}

	private Group add(String name, int parentId)
	{
		Group g = new Group();
		g.setName(name);
		g.setDescription(name + " desc");
		g.setParentId(parentId);
		this.groups.addNew(g);
		return g;
	}

	private void user(int id, Integer... groupIds)
	{
		UserDocument u = new UserDocument();
		u.setId(id);
		u.setUsername("u" + id);
		u.setUsernameLower("u" + id);
		u.setGroupIds(List.of(groupIds));
		this.mongo.insert(u);
	}

	private static List<String> names(List groups)
	{
		return ((List<Group>)groups).stream().map(Group::getName).toList();
	}

	@Test
	void registeredAsStoreBeans()
	{
		assertSame(this.groups, Stores.group());
		assertSame(this.tree, Stores.treegroup());
	}

	@Test
	void crudAndOrderingByName()
	{
		Group admin = this.add("admin", 0);
		Group general = this.add("General", 0);
		Group beta = this.add("Beta", admin.getId());
		assertEquals(admin.getId() + 1, general.getId());

		Group loaded = this.groups.selectById(beta.getId());
		assertEquals("Beta", loaded.getName());
		assertEquals("Beta desc", loaded.getDescription());
		assertEquals(admin.getId(), loaded.getParentId());
		assertEquals(0, this.groups.selectById(9999).getId());

		// GroupModel.selectAll: ORDER BY group_name (case-insensitive collation)
		assertEquals(List.of("admin", "Beta", "General"), names(this.groups.selectAll()));

		loaded.setName("Zeta");
		loaded.setDescription("changed");
		loaded.setParentId(0);
		this.groups.update(loaded);
		Group updated = this.groups.selectById(beta.getId());
		assertEquals("Zeta", updated.getName());
		assertEquals("changed", updated.getDescription());
		assertEquals(0, updated.getParentId());
		assertEquals(List.of("admin", "General", "Zeta"), names(this.groups.selectAll()));
	}

	@Test
	void deleteOnlyWhenEmptyAndDropsRoles()
	{
		Group g = this.add("Mods", 0);
		Group empty = this.add("Empty", 0);
		this.user(1, g.getId());

		assertFalse(this.groups.canDelete(g.getId()));
		assertTrue(this.groups.canDelete(empty.getId()));

		Role role = new Role();
		role.setName("perm_administration");
		Stores.groupsecurity().addRole(empty.getId(), role);
		Stores.groupsecurity().addRole(g.getId(), role);

		this.groups.delete(empty.getId());
		assertEquals(List.of("Mods"), names(this.groups.selectAll()));
		assertEquals(1, this.mongo.count(new Query(), RoleDocument.class));
	}

	@Test
	void selectUsersIdsOrderedById()
	{
		Group g = this.add("Mods", 0);
		Group other = this.add("Other", 0);
		this.user(5, g.getId());
		this.user(2, g.getId(), other.getId());
		this.user(3, other.getId());

		assertEquals(List.of(2, 5), this.groups.selectUsersIds(g.getId()));
		assertEquals(List.of(2, 3), this.groups.selectUsersIds(other.getId()));
		assertTrue(this.groups.selectUsersIds(9999).isEmpty());
	}

	@Test
	void treeGroupsByParentOrderedByName()
	{
		Group root = this.add("Root", 0);
		this.add("another root", 0);
		this.add("Zchild", root.getId());
		Group a = this.add("achild", root.getId());

		List<GroupNode> roots = this.tree.selectGroups(0);
		assertEquals(List.of("another root", "Root"), roots.stream().map(GroupNode::getName).toList());

		List<GroupNode> children = this.tree.selectGroups(root.getId());
		assertEquals(List.of("achild", "Zchild"), children.stream().map(GroupNode::getName).toList());
		assertEquals(a.getId(), children.get(0).getId());
		assertTrue(this.tree.selectGroups(a.getId()).isEmpty());
	}
}
