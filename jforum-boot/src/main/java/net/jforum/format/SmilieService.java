package net.jforum.format;

import java.util.Comparator;
import java.util.List;

import net.jforum.domain.Smilie;
import net.jforum.repository.SmilieRepository;

import org.springframework.stereotype.Service;

/** Smilie replacement (legacy SmiliesRepository + PostCommon.processSmilies). */
@Service
public class SmilieService {

    private final SmilieRepository repository;
    private volatile List<Smilie> cache;

    public SmilieService(SmilieRepository repository) {
        this.repository = repository;
    }

    public List<Smilie> all() {
        List<Smilie> c = cache;
        if (c == null) {
            // Longer codes first so ":-)" wins over ":)"
            c = repository.findAll().stream()
                .sorted(Comparator.comparingInt((Smilie s) -> s.getCode() == null ? 0 : s.getCode().length()).reversed())
                .toList();
            cache = c;
        }
        return c;
    }

    public void invalidate() {
        cache = null;
    }

    public String imageTag(Smilie s) {
        return "<img src=\"/images/smilies/" + s.getDiskName() + "\" alt=\"\" class=\"smilie\" />";
    }

    public String process(String text) {
        StringBuilder sb = new StringBuilder(text);
        for (Smilie s : all()) {
            if (s.getCode() == null || s.getCode().isEmpty()) {
                continue;
            }
            String img = imageTag(s);
            int pos = sb.indexOf(s.getCode());
            int counter = 0;
            while (pos > -1 && counter++ < 300) {
                sb.replace(pos, pos + s.getCode().length(), img);
                pos = sb.indexOf(s.getCode(), pos + img.length());
            }
        }
        return sb.toString();
    }
}
