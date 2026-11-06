package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.List;

import net.jforum.document.CategoryDocument;
import net.jforum.document.ForumDocument;
import net.jforum.document.repository.CategoryDocumentRepository;
import net.jforum.entities.Category;
import net.jforum.persistence.CategoryStore;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class MongoCategoryStore implements CategoryStore
{
	private final CategoryDocumentRepository repository;
	private final MongoTemplate mongo;
	private final SequenceGenerator sequences;

	public MongoCategoryStore(CategoryDocumentRepository repository, MongoTemplate mongo, SequenceGenerator sequences)
	{
		this.repository = repository;
		this.mongo = mongo;
		this.sequences = sequences;
	}

	@Override
	public Category selectById(int categoryId)
	{
		return this.repository.findById(categoryId).map(MongoCategoryStore::toCategory).orElseGet(Category::new);
	}

	@Override
	public List selectAll()
	{
		List<Category> l = new ArrayList<>();

		for (CategoryDocument d : this.repository.findAll(Sort.by("displayOrder", "id"))) {
			l.add(toCategory(d));
		}

		return l;
	}

	@Override
	public boolean canDelete(int categoryId)
	{
		return !this.mongo.exists(new Query(Criteria.where("categoriesId").is(categoryId)), ForumDocument.class);
	}

	@Override
	public void delete(int categoryId)
	{
		this.repository.deleteById(categoryId);
	}

	@Override
	public void update(Category category)
	{
		this.mongo.updateFirst(new Query(Criteria.where("_id").is(category.getId())),
			new Update().set("title", category.getName()).set("moderated", category.isModerated()),
			CategoryDocument.class);
	}

	@Override
	public int addNew(Category category)
	{
		CategoryDocument max = this.mongo.findOne(new Query().with(Sort.by(Sort.Direction.DESC, "displayOrder")).limit(1),
			CategoryDocument.class);

		CategoryDocument d = new CategoryDocument();
		d.id = this.sequences.next("categories");
		d.title = category.getName();
		d.displayOrder = (max == null ? 0 : max.displayOrder) + 1;
		d.moderated = category.isModerated();
		this.repository.insert(d);

		category.setId(d.id);
		category.setOrder(d.displayOrder);
		return d.id;
	}

	@Override
	public void setOrderUp(Category category, Category otherCategory)
	{
		this.swapOrder(category, otherCategory);
	}

	@Override
	public void setOrderDown(Category category, Category otherCategory)
	{
		this.swapOrder(category, otherCategory);
	}

	private void swapOrder(Category category, Category otherCategory)
	{
		int tmpOrder = otherCategory.getOrder();
		otherCategory.setOrder(category.getOrder());
		category.setOrder(tmpOrder);

		this.setOrder(otherCategory);
		this.setOrder(category);
	}

	private void setOrder(Category c)
	{
		this.mongo.updateFirst(new Query(Criteria.where("_id").is(c.getId())),
			new Update().set("displayOrder", c.getOrder()), CategoryDocument.class);
	}

	private static Category toCategory(CategoryDocument d)
	{
		Category c = new Category();
		c.setId(d.id);
		c.setName(d.title);
		c.setOrder(d.displayOrder);
		c.setModerated(d.moderated);
		return c;
	}
}
