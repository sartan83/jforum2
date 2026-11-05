package net.jforum.domain;

import java.util.ArrayList;
import java.util.List;

/** A permission granted to a group. For 'multiple' permissions, values whitelists the ids (forum/category) the role applies to. */
public class Role {

    private String name;
    private List<String> values = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }
}
