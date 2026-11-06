package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_bookmarks</code>.
 */
@Document(collection = BookmarkDocument.COLLECTION)
@CompoundIndex(name = "user_type_relation", def = "{'userId': 1, 'relationType': 1, 'relationId': 1}")
public class BookmarkDocument
{
	public static final String COLLECTION = "bookmarks";

	@Id
	private int id;
	private int userId;
	@Indexed
	private int relationId;
	private int relationType;
	private boolean publicVisible;
	private String title;
	private String description;

	public int getId() { return this.id; }
	public void setId(int id) { this.id = id; }
	public int getUserId() { return this.userId; }
	public void setUserId(int userId) { this.userId = userId; }
	public int getRelationId() { return this.relationId; }
	public void setRelationId(int relationId) { this.relationId = relationId; }
	public int getRelationType() { return this.relationType; }
	public void setRelationType(int relationType) { this.relationType = relationType; }
	public boolean isPublicVisible() { return this.publicVisible; }
	public void setPublicVisible(boolean publicVisible) { this.publicVisible = publicVisible; }
	public String getTitle() { return this.title; }
	public void setTitle(String title) { this.title = title; }
	public String getDescription() { return this.description; }
	public void setDescription(String description) { this.description = description; }
}
