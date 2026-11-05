package net.jforum.format;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Loads the BBCode definitions from jforum/bb_config.xml (legacy BBCodeHandler). */
@Component
public class BBCodeRepository {

    private final List<BBCode> bbList;
    private final List<BBCode> alwaysProcess;

    public BBCodeRepository() {
        this("jforum/bb_config.xml");
    }

    public BBCodeRepository(String classpathLocation) {
        List<BBCode> normal = new ArrayList<>();
        List<BBCode> always = new ArrayList<>();
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = f.newDocumentBuilder().parse(in);
            NodeList matches = doc.getElementsByTagName("match");
            for (int i = 0; i < matches.getLength(); i++) {
                Element m = (Element) matches.item(i);
                String regex = m.getElementsByTagName("regex").item(0).getTextContent().trim();
                String replace = m.getElementsByTagName("replace").item(0).getTextContent().trim();
                BBCode bb = new BBCode(m.getAttribute("name"), Pattern.compile(regex), replace,
                    "true".equals(m.getAttribute("alwaysProcess")));
                (bb.alwaysProcess() ? always : normal).add(bb);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse " + classpathLocation, e);
        }
        this.bbList = Collections.unmodifiableList(normal);
        this.alwaysProcess = Collections.unmodifiableList(always);
    }

    public List<BBCode> getBbList() {
        return bbList;
    }

    public List<BBCode> getAlwaysProcessList() {
        return alwaysProcess;
    }
}
