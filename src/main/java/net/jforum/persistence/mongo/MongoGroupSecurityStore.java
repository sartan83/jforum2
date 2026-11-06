package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.jforum.document.RoleDocument;
import net.jforum.entities.Group;
import net.jforum.entities.User;
import net.jforum.persistence.GroupSecurityStore;
import net.jforum.repository.RolesRepository;
import net.jforum.security.Role;
import net.jforum.security.RoleCollection;
import net.jforum.security.RoleValue;
import net.jforum.security.RoleValueCollection;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * MongoDB implementation of {@link GroupSecurityStore} (former <code>GenericGroupSecurityDAO</code>
 * and <code>SecurityCommon</code>). Each role is a document of the <code>roles</code> collection with
 * its values embedded in <code>roleValues</code>.
 */
@Component
public class MongoGroupSecurityStore implements GroupSecurityStore
{
	/** Roles whose values are forum ids (PermissionControl.selectForumRoles). */
	static final List<String> FORUM_ROLES = List.of("perm_forum", "perm_anonymous_post", "perm_reply_only",
		"perm_read_only_forums", "perm_reply_without_moderation", "perm_html_disabled", "perm_attachments_enabled",
		"perm_moderation_forums");

	private final MongoTemplate mongo;
	private final SequenceGenerator sequences;

	public MongoGroupSecurityStore(MongoTemplate mongo, SequenceGenerator sequences)
	{
		this.mongo = mongo;
		this.sequences = sequences;
	}

	public void deleteForumRoles(int forumId)
	{
		String value = String.valueOf(forumId);

		this.mongo.updateMulti(new Query(Criteria.where("name").in(FORUM_ROLES).and("roleValues").is(value)),
			new Update().pull("roleValues", value), RoleDocument.class);
	}

	public void deleteAllRoles(int groupId)
	{
		this.mongo.remove(new Query(Criteria.where("groupId").is(groupId)), RoleDocument.class);
	}

	public void addRole(int groupId, Role role)
	{
		this.addRole(groupId, role, null);
	}

	public void addRole(int groupId, Role role, RoleValueCollection roleValues)
	{
		RoleDocument d = new RoleDocument();
		d.setId(this.sequences.next(RoleDocument.COLLECTION));
		d.setGroupId(groupId);
		d.setName(role.getName());

		if (roleValues != null) {
			d.setRoleValues(values(roleValues));
		}

		this.mongo.insert(d);
	}

	private static List<String> values(RoleValueCollection rvc)
	{
		List<String> l = new ArrayList<>();

		for (Iterator iter = rvc.iterator(); iter.hasNext();) {
			l.add(((RoleValue)iter.next()).getValue());
		}

		return l;
	}

	public void addRoleValue(int groupId, Role role, RoleValueCollection rvc)
	{
		Query q = new Query(Criteria.where("name").is(role.getName()).and("groupId").is(groupId)).with(Sort.by("_id"));
		RoleDocument existing = this.mongo.findOne(q, RoleDocument.class);

		if (existing == null) {
			this.addRole(groupId, role, rvc);
		}
		else {
			this.mongo.updateFirst(new Query(Criteria.where("_id").is(existing.getId())),
				new Update().addToSet("roleValues").each(values(rvc).toArray()), RoleDocument.class);
		}
	}

	public RoleCollection loadRoles(int groupId)
	{
		return this.loadRoles(new int[] { groupId });
	}

	/**
	 * PermissionControl.loadGroupRoles: one {@link Role} per distinct name (ordered by name) of the given
	 * groups, with the union of the values. As in the legacy UNION query, every role also gets the value "0".
	 */
	protected RoleCollection loadRoles(int[] groupIds)
	{
		List<Integer> ids = Arrays.stream(groupIds).boxed().toList();
		Query q = new Query(Criteria.where("groupId").in(ids)).with(Sort.by("name", "_id"));

		Map<String, Role> byName = new LinkedHashMap<>();

		for (RoleDocument d : this.mongo.find(q, RoleDocument.class)) {
			Role r = byName.get(d.getName());

			if (r == null) {
				r = new Role();
				r.setName(d.getName());
				r.getValues().add(new RoleValue("0"));
				byName.put(d.getName(), r);
			}

			for (String value : d.getRoleValues()) {
				if (StringUtils.isNotBlank(value)) {
					r.getValues().add(new RoleValue(value));
				}
			}
		}

		RoleCollection rc = new RoleCollection();
		byName.values().forEach(rc::add);

		return rc;
	}

	public RoleCollection loadRolesByUserGroups(User user)
	{
		List groups = user.getGroupsList();
		int[] groupIds = new int[groups.size()];

		for (int i = 0; i < groupIds.length; i++) {
			groupIds[i] = ((Group)groups.get(i)).getId();
		}

		Arrays.sort(groupIds);

		RoleCollection groupRoles = RolesRepository.getGroupRoles(groupIds);

		if (groupRoles == null) {
			groupRoles = this.loadRoles(groupIds);
			RolesRepository.addGroupRoles(groupIds, groupRoles);
		}

		return groupRoles;
	}
}
