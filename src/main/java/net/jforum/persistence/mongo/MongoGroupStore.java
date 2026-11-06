package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.jforum.document.GroupDocument;
import net.jforum.document.UserDocument;
import net.jforum.document.repository.GroupDocumentRepository;
import net.jforum.entities.Group;
import net.jforum.persistence.GroupSecurityStore;
import net.jforum.persistence.GroupStore;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * MongoDB implementation of {@link GroupStore} (former <code>GenericGroupDAO</code>).
 */
@Component
public class MongoGroupStore implements GroupStore
{
	/** Case-insensitive ordering, like the default collation of the legacy databases. */
	static final Collation CASE_INSENSITIVE = Collation.of(Locale.ENGLISH).strength(Collation.ComparisonLevel.secondary());

	private final MongoTemplate mongo;
	private final GroupDocumentRepository groups;
	private final SequenceGenerator sequences;
	private final GroupSecurityStore security;

	public MongoGroupStore(MongoTemplate mongo, GroupDocumentRepository groups, SequenceGenerator sequences,
		GroupSecurityStore security)
	{
		this.mongo = mongo;
		this.groups = groups;
		this.sequences = sequences;
		this.security = security;
	}

	static Group toGroup(GroupDocument d)
	{
		return new Group(d.getId(), d.getParentId() == null ? 0 : d.getParentId(), d.getGroupName(),
			d.getGroupDescription());
	}

	public Group selectById(int groupId)
	{
		return this.groups.findById(groupId).map(MongoGroupStore::toGroup).orElseGet(Group::new);
	}

	public List selectAll()
	{
		Query q = new Query().with(Sort.by("groupName", "_id")).collation(CASE_INSENSITIVE);

		List l = new ArrayList();

		for (GroupDocument d : this.mongo.find(q, GroupDocument.class)) {
			l.add(toGroup(d));
		}

		return l;
	}

	public boolean canDelete(int groupId)
	{
		return !this.mongo.exists(new Query(Criteria.where("groupIds").is(groupId)), UserDocument.class);
	}

	public void delete(int groupId)
	{
		this.groups.deleteById(groupId);
		this.security.deleteAllRoles(groupId);
	}

	public void update(Group group)
	{
		GroupDocument d = this.groups.findById(group.getId()).orElse(null);

		if (d == null) {
			return;
		}

		d.setGroupName(group.getName());
		d.setParentId(group.getParentId());
		d.setGroupDescription(group.getDescription());
		this.groups.save(d);
	}

	public void addNew(Group group)
	{
		GroupDocument d = new GroupDocument();
		d.setId(this.sequences.next(GroupDocument.COLLECTION));
		d.setGroupName(group.getName());
		d.setGroupDescription(group.getDescription());
		d.setParentId(group.getParentId());
		this.groups.insert(d);

		group.setId(d.getId());
	}

	public List selectUsersIds(int groupId)
	{
		Query q = new Query(Criteria.where("groupIds").is(groupId)).with(Sort.by("_id"));
		q.fields().include("_id");

		List l = new ArrayList();

		for (UserDocument d : this.mongo.find(q, UserDocument.class)) {
			l.add(Integer.valueOf(d.getId()));
		}

		return l;
	}
}
