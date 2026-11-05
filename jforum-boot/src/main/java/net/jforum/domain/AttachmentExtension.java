package net.jforum.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Allowed/denied attachment extension (legacy jforum_extensions). */
@Document("extensions")
public class AttachmentExtension {

    @Id private Integer id;
    private Integer extensionGroupId;
    private String description;
    private String uploadIcon;
    private String extension;
    private boolean allow = true;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getExtensionGroupId() {
        return extensionGroupId;
    }

    public void setExtensionGroupId(Integer extensionGroupId) {
        this.extensionGroupId = extensionGroupId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUploadIcon() {
        return uploadIcon;
    }

    public void setUploadIcon(String uploadIcon) {
        this.uploadIcon = uploadIcon;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public boolean isAllow() {
        return allow;
    }

    public void setAllow(boolean allow) {
        this.allow = allow;
    }
}
