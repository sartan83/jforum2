package net.jforum.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A poll embedded in its topic (legacy jforum_vote_desc/results/voters). */
public class Poll {

    private Integer id;
    private String label;
    private Instant startTime;
    private int length;
    private List<PollOption> options = new ArrayList<>();
    private List<PollVoter> voters = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public List<PollOption> getOptions() {
        return options;
    }

    public void setOptions(List<PollOption> options) {
        this.options = options;
    }

    public List<PollVoter> getVoters() {
        return voters;
    }

    public void setVoters(List<PollVoter> voters) {
        this.voters = voters;
    }
}
