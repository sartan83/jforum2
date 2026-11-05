package net.jforum.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** A forum (legacy jforum_forums), with denormalised counters and last post info. */
@Document("forums")
public class Forum {

    @Id private Integer id;
    @Indexed private Integer categoryId;
    private String name;
    private String description;
    private int order;
    private boolean moderated;
    private int totalTopics;
    private int totalPosts;
    private Integer lastPostId;
    private LastPostInfo lastPostInfo;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isModerated() {
        return moderated;
    }

    public void setModerated(boolean moderated) {
        this.moderated = moderated;
    }

    public int getTotalTopics() {
        return totalTopics;
    }

    public void setTotalTopics(int totalTopics) {
        this.totalTopics = totalTopics;
    }

    public int getTotalPosts() {
        return totalPosts;
    }

    public void setTotalPosts(int totalPosts) {
        this.totalPosts = totalPosts;
    }

    public Integer getLastPostId() {
        return lastPostId;
    }

    public void setLastPostId(Integer lastPostId) {
        this.lastPostId = lastPostId;
    }

    public LastPostInfo getLastPostInfo() {
        return lastPostInfo;
    }

    public void setLastPostInfo(LastPostInfo lastPostInfo) {
        this.lastPostInfo = lastPostInfo;
    }
}
