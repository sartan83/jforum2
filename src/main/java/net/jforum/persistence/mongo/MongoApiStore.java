package net.jforum.persistence.mongo;

import net.jforum.document.ApiDocument;
import net.jforum.persistence.ApiStore;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * MongoDB implementation of {@link ApiStore} (former <code>ApiModel.isValid</code>: the key only has to exist,
 * <code>api_validity</code> is not checked).
 */
@Component
public class MongoApiStore implements ApiStore
{
	private final MongoTemplate mongo;

	public MongoApiStore(MongoTemplate mongo)
	{
		this.mongo = mongo;
	}

	public boolean isValid(String apiKey)
	{
		if (apiKey == null) {
			return false;
		}

		return this.mongo.exists(new Query(Criteria.where("apiKey").is(apiKey)), ApiDocument.class);
	}
}
