package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_groups</code> table.
 */
@Document(collection = GroupDocument.COLLECTION)
public class GroupDocument
{
	public static final String COLLECTION = "groups";

	@Id
	private int id;

	@Indexed
	private String groupName;
	private String groupDescription;

	@Indexed
	private Integer parentId;

	public int getId()
	{
		return this.id;
	}

	public void setId(int id)
	{
		this.id = id;
	}

	public String getGroupName()
	{
		return this.groupName;
	}

	public void setGroupName(String groupName)
	{
		this.groupName = groupName;
	}

	public String getGroupDescription()
	{
		return this.groupDescription;
	}

	public void setGroupDescription(String groupDescription)
	{
		this.groupDescription = groupDescription;
	}

	public Integer getParentId()
	{
		return this.parentId;
	}

	public void setParentId(Integer parentId)
	{
		this.parentId = parentId;
	}
}
