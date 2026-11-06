package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.List;

import net.jforum.document.ConfigDocument;
import net.jforum.document.repository.ConfigDocumentRepository;
import net.jforum.entities.Config;
import net.jforum.persistence.ConfigStore;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class MongoConfigStore implements ConfigStore
{
	private final ConfigDocumentRepository repository;
	private final MongoTemplate mongo;
	private final SequenceGenerator sequences;

	public MongoConfigStore(ConfigDocumentRepository repository, MongoTemplate mongo, SequenceGenerator sequences)
	{
		this.repository = repository;
		this.mongo = mongo;
		this.sequences = sequences;
	}

	@Override
	public void insert(Config config)
	{
		ConfigDocument d = new ConfigDocument();
		d.id = this.sequences.next("config");
		d.configName = config.getName();
		d.configValue = config.getValue();
		this.repository.insert(d);
		config.setId(d.id);
	}

	@Override
	public void update(Config config)
	{
		this.mongo.updateMulti(new Query(Criteria.where("configName").is(config.getName())),
			new Update().set("configValue", config.getValue()), ConfigDocument.class);
	}

	@Override
	public void delete(Config config)
	{
		this.repository.deleteById(config.getId());
	}

	@Override
	public List selectAll()
	{
		List<Config> l = new ArrayList<>();

		for (ConfigDocument d : this.repository.findAll(Sort.by("id"))) {
			l.add(toConfig(d));
		}

		return l;
	}

	@Override
	public Config selectByName(String name)
	{
		ConfigDocument d = this.mongo.findOne(new Query(Criteria.where("configName").is(name)), ConfigDocument.class);
		return d == null ? null : toConfig(d);
	}

	private static Config toConfig(ConfigDocument d)
	{
		Config c = new Config();
		c.setId(d.id);
		c.setName(d.configName);
		c.setValue(d.configValue);
		return c;
	}
}
