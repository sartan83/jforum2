package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.jforum.boot.JForumApplication;
import net.jforum.cache.DefaultCacheEngine;
import net.jforum.document.RoleDocument;
import net.jforum.entities.Group;
import net.jforum.entities.User;
import net.jforum.persistence.GroupSecurityStore;
import net.jforum.persistence.Stores;
import net.jforum.repository.RolesRepository;
import net.jforum.security.PermissionControl;
import net.jforum.security.Role;
import net.jforum.security.RoleCollection;
import net.jforum.security.RoleValue;
import net.jforum.security.RoleValueCollection;
import net.jforum.security.SecurityConstants;
import net.jforum.support.MongoTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoGroupSecurityStoreTest extends MongoTestSupport
{
	@Autowired
	private GroupSecurityStore security;

	@Autowired
	private MongoTemplate mongo;

	@BeforeEach
	void clean()
	{
		this.mongo.remove(new Query(), RoleDocument.class);

		DefaultCacheEngine cache = new DefaultCacheEngine();
		cache.init();
		new RolesRepository().setCacheEngine(cache);
	}

	private static Role role(String name)
	{
		Role r = new Role();
		r.setName(name);
		return r;
	}

	private static RoleValueCollection values(String... values)
	{
		RoleValueCollection rvc = new RoleValueCollection();

		for (String v : values) {
			rvc.add(new RoleValue(v));
		}

		return rvc;
	}

	private static Set<String> valuesOf(Role role)
	{
		return ((Set<RoleValue>)role.getValues()).stream().map(RoleValue::getValue).collect(Collectors.toSet());
	}

	@Test
	void registeredAsStoreBean()
	{
		assertSame(this.security, Stores.groupsecurity());
	}

	@Test
	void addRoleAndRoleValuesPerForum()
	{
		this.security.addRole(1, role(SecurityConstants.PERM_ADMINISTRATION));
		this.security.addRole(1, role(SecurityConstants.PERM_FORUM), values("10", "11"));
		this.security.addRoleValue(1, role(SecurityConstants.PERM_FORUM), values("12", "10"));
		// addRoleValue creates the role when the group does not have it yet
		this.security.addRoleValue(1, role(SecurityConstants.PERM_READ_ONLY_FORUMS), values("11"));

		RoleCollection rc = this.security.loadRoles(1);
		assertEquals(List.of(SecurityConstants.PERM_ADMINISTRATION, SecurityConstants.PERM_FORUM,
			SecurityConstants.PERM_READ_ONLY_FORUMS), List.copyOf(rc.keySet()));
		assertEquals(Set.of("0"), valuesOf(rc.get(SecurityConstants.PERM_ADMINISTRATION)));
		assertEquals(Set.of("0", "10", "11", "12"), valuesOf(rc.get(SecurityConstants.PERM_FORUM)));

		RoleDocument doc = this.mongo.findOne(new Query(), RoleDocument.class);
		assertNotNull(doc);
		assertEquals(3, this.mongo.count(new Query(), RoleDocument.class));

		PermissionControl pc = new PermissionControl();
		pc.setRoles(rc);
		assertTrue(pc.canAccess(SecurityConstants.PERM_ADMINISTRATION));
		assertTrue(pc.canAccess(SecurityConstants.PERM_FORUM, "10"));
		assertTrue(pc.canAccess(SecurityConstants.PERM_FORUM, "12"));
		assertFalse(pc.canAccess(SecurityConstants.PERM_FORUM, "13"));
		assertFalse(pc.canAccess(SecurityConstants.PERM_MODERATION));
	}

	@Test
	void loadRolesIsolatesGroups()
	{
		this.security.addRole(1, role(SecurityConstants.PERM_FORUM), values("10"));
		this.security.addRole(2, role(SecurityConstants.PERM_FORUM), values("20"));
		this.security.addRole(2, role(SecurityConstants.PERM_MODERATION));

		RoleCollection g1 = this.security.loadRoles(1);
		assertEquals(1, g1.size());
		assertEquals(Set.of("0", "10"), valuesOf(g1.get(SecurityConstants.PERM_FORUM)));
		assertNull(g1.get(SecurityConstants.PERM_MODERATION));
		assertTrue(this.security.loadRoles(99).isEmpty());
	}

	@Test
	void loadRolesByUserGroupsMergesAndCaches()
	{
		this.security.addRole(1, role(SecurityConstants.PERM_FORUM), values("10"));
		this.security.addRole(2, role(SecurityConstants.PERM_FORUM), values("20"));
		this.security.addRole(2, role(SecurityConstants.PERM_MODERATION));

		User user = new User();
		user.getGroupsList().add(new Group(2, 0, "Mods", ""));
		user.getGroupsList().add(new Group(1, 0, "General", ""));

		RoleCollection merged = this.security.loadRolesByUserGroups(user);
		assertEquals(Set.of("0", "10", "20"), valuesOf(merged.get(SecurityConstants.PERM_FORUM)));
		assertNotNull(merged.get(SecurityConstants.PERM_MODERATION));
		assertSame(merged, RolesRepository.getGroupRoles(new int[] { 1, 2 }));
		assertSame(merged, this.security.loadRolesByUserGroups(user));
	}

	@Test
	void deleteForumRolesRemovesForumValueFromAllGroups()
	{
		this.security.addRole(1, role(SecurityConstants.PERM_FORUM), values("10", "11"));
		this.security.addRole(2, role(SecurityConstants.PERM_FORUM), values("10"));
		this.security.addRole(2, role(SecurityConstants.PERM_MODERATION_FORUMS), values("10"));
		// Not a forum role: its "10" must survive
		this.security.addRole(2, role(SecurityConstants.PERM_CATEGORY), values("10"));

		this.security.deleteForumRoles(10);

		assertEquals(Set.of("0", "11"), valuesOf(this.security.loadRoles(1).get(SecurityConstants.PERM_FORUM)));
		RoleCollection g2 = this.security.loadRoles(2);
		assertEquals(Set.of("0"), valuesOf(g2.get(SecurityConstants.PERM_FORUM)));
		assertEquals(Set.of("0"), valuesOf(g2.get(SecurityConstants.PERM_MODERATION_FORUMS)));
		assertEquals(Set.of("0", "10"), valuesOf(g2.get(SecurityConstants.PERM_CATEGORY)));
	}

	@Test
	void deleteAllRolesOfGroup()
	{
		this.security.addRole(1, role(SecurityConstants.PERM_FORUM), values("10"));
		this.security.addRole(2, role(SecurityConstants.PERM_FORUM), values("20"));

		this.security.deleteAllRoles(1);

		assertTrue(this.security.loadRoles(1).isEmpty());
		assertEquals(1, this.security.loadRoles(2).size());
	}
}
