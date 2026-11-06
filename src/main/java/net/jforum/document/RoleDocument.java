package net.jforum.document;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_roles</code> table, with the <code>jforum_role_values</code> rows embedded as <code>roleValues</code>.
 */
@Document(collection = RoleDocument.COLLECTION)
@CompoundIndex(name = "group_name", def = "{'groupId': 1, 'name': 1}")
public class RoleDocument
{
	public static final String COLLECTION = "roles";

	@Id
	private int id;
	private Integer groupId;

	@Indexed
	private String name;
	private List<String> roleValues = new ArrayList<>();

	public int getId()
	{
		return this.id;
	}

	public void setId(int id)
	{
		this.id = id;
	}

	public Integer getGroupId()
	{
		return this.groupId;
	}

	public void setGroupId(Integer groupId)
	{
		this.groupId = groupId;
	}

	public String getName()
	{
		return this.name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public List<String> getRoleValues()
	{
		return this.roleValues;
	}

	public void setRoleValues(List<String> roleValues)
	{
		this.roleValues = roleValues;
	}
}
