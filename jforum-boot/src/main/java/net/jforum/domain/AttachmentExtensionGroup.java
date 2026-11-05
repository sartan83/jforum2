package net.jforum.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Attachment extension group (legacy jforum_extension_groups). */
@Document("extension_groups")
public class AttachmentExtensionGroup {

    @Id private Integer id;
    private String name;
    private boolean allow = true;
    private String uploadIcon;
    private int downloadMode = 1;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isAllow() {
        return allow;
    }

    public void setAllow(boolean allow) {
        this.allow = allow;
    }

    public String getUploadIcon() {
        return uploadIcon;
    }

    public void setUploadIcon(String uploadIcon) {
        this.uploadIcon = uploadIcon;
    }

    public int getDownloadMode() {
        return downloadMode;
    }

    public void setDownloadMode(int downloadMode) {
        this.downloadMode = downloadMode;
    }
}
