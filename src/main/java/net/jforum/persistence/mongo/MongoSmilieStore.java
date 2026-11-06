package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.List;

import net.jforum.document.SmilieDocument;
import net.jforum.document.repository.SmilieDocumentRepository;
import net.jforum.entities.Smilie;
import net.jforum.persistence.SmilieStore;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class MongoSmilieStore implements SmilieStore
{
	private final SmilieDocumentRepository repository;
	private final SequenceGenerator sequences;

	public MongoSmilieStore(SmilieDocumentRepository repository, SequenceGenerator sequences)
	{
		this.repository = repository;
		this.sequences = sequences;
	}

	@Override
	public int addNew(Smilie smilie)
	{
		SmilieDocument doc = new SmilieDocument();
		doc.setId(this.sequences.next(SmilieDocument.COLLECTION));
		copy(smilie, doc);
		this.repository.insert(doc);
		return doc.getId();
	}

	@Override
	public void delete(int id)
	{
		this.repository.deleteById(id);
	}

	@Override
	public void update(Smilie smilie)
	{
		this.repository.findById(smilie.getId()).ifPresent(doc -> {
			copy(smilie, doc);
			this.repository.save(doc);
		});
	}

	@Override
	public List selectAll()
	{
		List<Smilie> l = new ArrayList<>();

		for (SmilieDocument doc : this.repository.findAll(Sort.by("id"))) {
			l.add(toEntity(doc));
		}

		return l;
	}

	@Override
	public Smilie selectById(int id)
	{
		return this.repository.findById(id).map(MongoSmilieStore::toEntity).orElseGet(Smilie::new);
	}

	private static void copy(Smilie smilie, SmilieDocument doc)
	{
		doc.setCode(smilie.getCode());
		doc.setUrl(smilie.getUrl());
		doc.setDiskName(smilie.getDiskName());
	}

	private static Smilie toEntity(SmilieDocument doc)
	{
		Smilie s = new Smilie();
		s.setId(doc.getId());
		s.setCode(doc.getCode());
		s.setUrl(doc.getUrl());
		s.setDiskName(doc.getDiskName());
		return s;
	}
}
