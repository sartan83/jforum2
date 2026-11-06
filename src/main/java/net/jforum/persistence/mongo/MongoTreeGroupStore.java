package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.List;

import net.jforum.document.GroupDocument;
import net.jforum.persistence.TreeGroupStore;
import net.jforum.util.GroupNode;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * MongoDB implementation of {@link TreeGroupStore} (former <code>TreeGroup.selectGroup</code>).
 */
@Component
public class MongoTreeGroupStore implements TreeGroupStore
{
	private final MongoTemplate mongo;

	public MongoTreeGroupStore(MongoTemplate mongo)
	{
		this.mongo = mongo;
	}

	public List selectGroups(int parentId)
	{
		Criteria c = parentId == 0
			? new Criteria().orOperator(Criteria.where("parentId").is(0), Criteria.where("parentId").is(null))
			: Criteria.where("parentId").is(parentId);

		Query q = new Query(c).with(Sort.by("groupName", "_id")).collation(MongoGroupStore.CASE_INSENSITIVE);

		List list = new ArrayList();

		for (GroupDocument d : this.mongo.find(q, GroupDocument.class)) {
			GroupNode n = new GroupNode();
			n.setName(d.getGroupName());
			n.setId(d.getId());
			list.add(n);
		}

		return list;
	}
}
