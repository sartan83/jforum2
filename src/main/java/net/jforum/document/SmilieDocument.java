package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_smilies</code>.
 */
@Document(collection = SmilieDocument.COLLECTION)
public class SmilieDocument
{
	public static final String COLLECTION = "smilies";

	@Id
	private int id;
	private String code;
	private String url;
	private String diskName;

	public int getId() { return this.id; }
	public void setId(int id) { this.id = id; }
	public String getCode() { return this.code; }
	public void setCode(String code) { this.code = code; }
	public String getUrl() { return this.url; }
	public void setUrl(String url) { this.url = url; }
	public String getDiskName() { return this.diskName; }
	public void setDiskName(String diskName) { this.diskName = diskName; }
}
