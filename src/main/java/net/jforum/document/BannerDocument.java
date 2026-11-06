package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_banner</code> (columns without the <code>banner_</code> prefix).
 */
@Document(collection = BannerDocument.COLLECTION)
@CompoundIndex(name = "placement_active_weight", def = "{'placement': 1, 'active': 1, 'weight': 1}")
public class BannerDocument
{
	public static final String COLLECTION = "banner";

	@Id
	private int id;
	private String name;
	private int placement;
	private String description;
	private int clicks;
	private int views;
	private String url;
	private int weight;
	private boolean active;
	private String comment;
	private int type;
	private int width;
	private int height;

	public int getId() { return this.id; }
	public void setId(int id) { this.id = id; }
	public String getName() { return this.name; }
	public void setName(String name) { this.name = name; }
	public int getPlacement() { return this.placement; }
	public void setPlacement(int placement) { this.placement = placement; }
	public String getDescription() { return this.description; }
	public void setDescription(String description) { this.description = description; }
	public int getClicks() { return this.clicks; }
	public void setClicks(int clicks) { this.clicks = clicks; }
	public int getViews() { return this.views; }
	public void setViews(int views) { this.views = views; }
	public String getUrl() { return this.url; }
	public void setUrl(String url) { this.url = url; }
	public int getWeight() { return this.weight; }
	public void setWeight(int weight) { this.weight = weight; }
	public boolean isActive() { return this.active; }
	public void setActive(boolean active) { this.active = active; }
	public String getComment() { return this.comment; }
	public void setComment(String comment) { this.comment = comment; }
	public int getType() { return this.type; }
	public void setType(int type) { this.type = type; }
	public int getWidth() { return this.width; }
	public void setWidth(int width) { this.width = width; }
	public int getHeight() { return this.height; }
	public void setHeight(int height) { this.height = height; }
}
