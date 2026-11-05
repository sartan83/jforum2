package net.jforum.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Attachment metadata (legacy jforum_attach + jforum_attach_desc); bytes live in GridFS under gridFsId. */
@Document("attachments")
public class Attachment {

    @Id private Integer id;
    @Indexed private Integer postId;
    private Integer privmsgId;
    private Integer userId;
    private String gridFsId;
    private String physicalFilename;
    private String realFilename;
    private String description;
    private String mimetype;
    private long filesize;
    private int downloadCount;
    private Instant uploadTime;
    private boolean thumb;
    private Integer extensionId;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getPostId() {
        return postId;
    }

    public void setPostId(Integer postId) {
        this.postId = postId;
    }

    public Integer getPrivmsgId() {
        return privmsgId;
    }

    public void setPrivmsgId(Integer privmsgId) {
        this.privmsgId = privmsgId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getGridFsId() {
        return gridFsId;
    }

    public void setGridFsId(String gridFsId) {
        this.gridFsId = gridFsId;
    }

    public String getPhysicalFilename() {
        return physicalFilename;
    }

    public void setPhysicalFilename(String physicalFilename) {
        this.physicalFilename = physicalFilename;
    }

    public String getRealFilename() {
        return realFilename;
    }

    public void setRealFilename(String realFilename) {
        this.realFilename = realFilename;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMimetype() {
        return mimetype;
    }

    public void setMimetype(String mimetype) {
        this.mimetype = mimetype;
    }

    public long getFilesize() {
        return filesize;
    }

    public void setFilesize(long filesize) {
        this.filesize = filesize;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(int downloadCount) {
        this.downloadCount = downloadCount;
    }

    public Instant getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(Instant uploadTime) {
        this.uploadTime = uploadTime;
    }

    public boolean isThumb() {
        return thumb;
    }

    public void setThumb(boolean thumb) {
        this.thumb = thumb;
    }

    public Integer getExtensionId() {
        return extensionId;
    }

    public void setExtensionId(Integer extensionId) {
        this.extensionId = extensionId;
    }
}
