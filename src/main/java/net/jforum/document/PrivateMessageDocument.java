package net.jforum.document;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_privmsgs</code> + <code>jforum_privmsgs_text</code>: one document per mailbox copy,
 * with the message text embedded.
 */
@Document(collection = PrivateMessageDocument.COLLECTION)
@CompoundIndexes({
	@CompoundIndex(name = "to_type_date", def = "{'toUserId': 1, 'type': 1, 'date': -1}"),
	@CompoundIndex(name = "from_type_date", def = "{'fromUserId': 1, 'type': 1, 'date': -1}")
})
public class PrivateMessageDocument
{
	public static final String COLLECTION = "privmsgs";

	@Id
	private int id;
	private int type;
	private String subject;
	private int fromUserId;
	private int toUserId;
	private Date date;
	private String ip;
	private boolean enableBbcode;
	private boolean enableHtml;
	private boolean enableSmilies;
	private boolean attachSig;
	private String text;

	public int getId() { return this.id; }
	public void setId(int id) { this.id = id; }
	public int getType() { return this.type; }
	public void setType(int type) { this.type = type; }
	public String getSubject() { return this.subject; }
	public void setSubject(String subject) { this.subject = subject; }
	public int getFromUserId() { return this.fromUserId; }
	public void setFromUserId(int fromUserId) { this.fromUserId = fromUserId; }
	public int getToUserId() { return this.toUserId; }
	public void setToUserId(int toUserId) { this.toUserId = toUserId; }
	public Date getDate() { return this.date; }
	public void setDate(Date date) { this.date = date; }
	public String getIp() { return this.ip; }
	public void setIp(String ip) { this.ip = ip; }
	public boolean isEnableBbcode() { return this.enableBbcode; }
	public void setEnableBbcode(boolean enableBbcode) { this.enableBbcode = enableBbcode; }
	public boolean isEnableHtml() { return this.enableHtml; }
	public void setEnableHtml(boolean enableHtml) { this.enableHtml = enableHtml; }
	public boolean isEnableSmilies() { return this.enableSmilies; }
	public void setEnableSmilies(boolean enableSmilies) { this.enableSmilies = enableSmilies; }
	public boolean isAttachSig() { return this.attachSig; }
	public void setAttachSig(boolean attachSig) { this.attachSig = attachSig; }
	public String getText() { return this.text; }
	public void setText(String text) { this.text = text; }
}
