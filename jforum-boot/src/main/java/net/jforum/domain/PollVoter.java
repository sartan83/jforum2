package net.jforum.domain;



/** A poll vote (legacy jforum_vote_voters). */
public class PollVoter {

    private Integer userId;
    private String ip;

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}
