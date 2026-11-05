package net.jforum.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** REST API key (legacy jforum_api). */
@Document("api_keys")
public class ApiKey {

    @Id private Integer id;
    @Indexed(unique = true) private String apiKey;
    private Instant validity;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Instant getValidity() {
        return validity;
    }

    public void setValidity(Instant validity) {
        this.validity = validity;
    }
}
