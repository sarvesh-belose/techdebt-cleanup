package com.enterprise.techdebt.maven;

import org.springframework.stereotype.Component;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Targeted, formatting-preserving edits to a Maven {@code pom.xml}.
 *
 * Why DOM and not Maven Model: maven-model resolves and re-serializes the pom which
 * destroys whitespace, comment placement, and ordering. For our use case we need to
 * make the smallest possible textual change — bump a single &lt;version&gt; or a single
 * &lt;property&gt; — so the PR diff stays surgical and review remains fast. DOM lets us
 * touch only the affected text node. Maven Model is still used elsewhere (e.g.
 * dependency:tree parsing) where structural traversal matters.
 */
@Component
public class PomFixer {

    /**
     * Bump the {@code <version>} of a direct dependency. Handles both inline versions
     * and property-driven versions (e.g. {@code <version>${spring.version}</version>}).
     *
     * @return result describing what changed; {@code changed=false} if the dep isn't present
     */
    public PomFixResult bumpDirectDependency(Path pomPath, String groupId, String artifactId, String newVersion) {
        if (pomPath == null || !Files.isRegularFile(pomPath)) {
            throw new PomFixException("pom.xml not found: " + pomPath);
        }
        if (isBlank(groupId) || isBlank(artifactId) || isBlank(newVersion)) {
            throw new PomFixException("groupId, artifactId and newVersion are required");
        }

        Document doc = read(pomPath);
        Element root = doc.getDocumentElement();
        Element dependencies = firstChildElement(root, "dependencies");
        if (dependencies == null) {
            return new PomFixResult(false, groupId, artifactId, null, newVersion, null,
                    "pom has no <dependencies> block");
        }
        Element match = findDependency(dependencies, groupId, artifactId);
        if (match == null) {
            return new PomFixResult(false, groupId, artifactId, null, newVersion, null,
                    "dependency not declared directly in this pom");
        }
        Element versionEl = firstChildElement(match, "version");
        if (versionEl == null) {
            return new PomFixResult(false, groupId, artifactId, null, newVersion, null,
                    "dependency has no inline version (managed by parent BOM); fix the parent or use dependencyManagement");
        }

        String currentText = textOf(versionEl);
        if (currentText == null) {
            return new PomFixResult(false, groupId, artifactId, null, newVersion, null,
                    "<version> element is empty");
        }

        if (isPropertyRef(currentText)) {
            String propName = unwrapProperty(currentText);
            Element properties = firstChildElement(root, "properties");
            if (properties == null) {
                return new PomFixResult(false, groupId, artifactId, currentText, newVersion, null,
                        "version references property " + propName + " but <properties> block is missing");
            }
            Element propEl = firstChildElement(properties, propName);
            if (propEl == null) {
                return new PomFixResult(false, groupId, artifactId, currentText, newVersion, null,
                        "property " + propName + " not declared in this pom");
            }
            String previous = textOf(propEl);
            setText(propEl, newVersion);
            write(doc, pomPath);
            return new PomFixResult(true, groupId, artifactId, previous, newVersion,
                    "property:" + propName, null);
        }

        setText(versionEl, newVersion);
        write(doc, pomPath);
        return new PomFixResult(true, groupId, artifactId, currentText, newVersion,
                "dependency.version", null);
    }

    /**
     * Pure helper: applies the edit to a pom string and returns the new content.
     * Used by tests and dry-run preview without touching the filesystem.
     */
    public String previewBump(String pomXml, String groupId, String artifactId, String newVersion) {
        Document doc = readString(pomXml);
        Element root = doc.getDocumentElement();
        Element dependencies = firstChildElement(root, "dependencies");
        if (dependencies == null) return pomXml;
        Element match = findDependency(dependencies, groupId, artifactId);
        if (match == null) return pomXml;
        Element versionEl = firstChildElement(match, "version");
        if (versionEl == null) return pomXml;
        String currentText = textOf(versionEl);
        if (currentText == null) return pomXml;

        if (isPropertyRef(currentText)) {
            String propName = unwrapProperty(currentText);
            Element properties = firstChildElement(root, "properties");
            if (properties == null) return pomXml;
            Element propEl = firstChildElement(properties, propName);
            if (propEl == null) return pomXml;
            setText(propEl, newVersion);
        } else {
            setText(versionEl, newVersion);
        }
        return writeString(doc);
    }

    /**
     * G4 — Pin a transitive dependency to a safe version via {@code <dependencyManagement>}.
     * Adds the block + entry if absent, updates the version if the entry already exists.
     * Annotates the override with an XML comment naming the CVE and date because the
     * pin looks unmotivated to a future reader otherwise.
     */
    public PomFixResult overrideTransitive(Path pomPath, String groupId, String artifactId,
                                            String safeVersion, String cveId) {
        if (pomPath == null || !Files.isRegularFile(pomPath)) {
            throw new PomFixException("pom.xml not found: " + pomPath);
        }
        if (isBlank(groupId) || isBlank(artifactId) || isBlank(safeVersion)) {
            throw new PomFixException("groupId, artifactId, safeVersion required");
        }

        Document doc = read(pomPath);
        Element root = doc.getDocumentElement();
        Element depMgmt = firstChildElement(root, "dependencyManagement");
        if (depMgmt == null) {
            depMgmt = doc.createElement("dependencyManagement");
            Element deps = doc.createElement("dependencies");
            depMgmt.appendChild(deps);
            root.appendChild(depMgmt);
        }
        Element depsEl = firstChildElement(depMgmt, "dependencies");
        if (depsEl == null) {
            depsEl = doc.createElement("dependencies");
            depMgmt.appendChild(depsEl);
        }

        Element existing = findDependency(depsEl, groupId, artifactId);
        String previous = null;
        if (existing != null) {
            Element versionEl = firstChildElement(existing, "version");
            if (versionEl != null) {
                previous = textOf(versionEl);
                setText(versionEl, safeVersion);
            } else {
                Element v = doc.createElement("version");
                v.setTextContent(safeVersion);
                existing.appendChild(v);
            }
        } else {
            String marker = cveId != null && !cveId.isBlank()
                    ? cveId + " override pinned " + LocalDate.now()
                    : "CVE override pinned " + LocalDate.now();
            Comment c = doc.createComment(" " + marker + " ");
            depsEl.appendChild(c);
            Element dep = doc.createElement("dependency");
            Element g = doc.createElement("groupId"); g.setTextContent(groupId); dep.appendChild(g);
            Element a = doc.createElement("artifactId"); a.setTextContent(artifactId); dep.appendChild(a);
            Element v = doc.createElement("version"); v.setTextContent(safeVersion); dep.appendChild(v);
            depsEl.appendChild(dep);
        }
        write(doc, pomPath);
        return new PomFixResult(true, groupId, artifactId, previous, safeVersion,
                "dependencyManagement", "transitive pinned via dependencyManagement");
    }

    /**
     * G6 — Walk an aggregator pom's {@code <modules>} and find the module pom that
     * declares the named dependency directly. Returns the project root pom when the
     * project isn't an aggregator (single-module project) or when no module declares
     * the dependency (so the caller can fall back to a dependencyManagement override).
     */
    public Optional<Path> findOwningModule(Path rootPomPath, String groupId, String artifactId) {
        if (rootPomPath == null || !Files.isRegularFile(rootPomPath)) {
            return Optional.empty();
        }
        Document root = read(rootPomPath);
        Element modulesEl = firstChildElement(root.getDocumentElement(), "modules");
        if (modulesEl == null) {
            // Not an aggregator. Check the root itself.
            return declaresDependency(root, groupId, artifactId)
                    ? Optional.of(rootPomPath)
                    : Optional.empty();
        }
        Path rootDir = rootPomPath.getParent();
        if (rootDir == null) return Optional.empty();

        List<Path> candidates = new ArrayList<>();
        NodeList modules = modulesEl.getElementsByTagName("module");
        for (int i = 0; i < modules.getLength(); i++) {
            Element m = (Element) modules.item(i);
            String moduleName = textOf(m);
            if (moduleName == null || moduleName.isBlank()) continue;
            Path modulePom = rootDir.resolve(moduleName).resolve("pom.xml");
            if (!Files.isRegularFile(modulePom)) continue;
            candidates.add(modulePom);
        }
        for (Path candidate : candidates) {
            Document childDoc = read(candidate);
            if (declaresDependency(childDoc, groupId, artifactId)) {
                return Optional.of(candidate);
            }
        }
        // Fall through: dependency may live only in a child of a child, or be transitive.
        // Caller (orchestrator) can choose between overrideTransitive on root, or skip.
        return Optional.empty();
    }

    private boolean declaresDependency(Document doc, String groupId, String artifactId) {
        Element deps = firstChildElement(doc.getDocumentElement(), "dependencies");
        if (deps == null) return false;
        return findDependency(deps, groupId, artifactId) != null;
    }

    private Element findDependency(Element dependencies, String groupId, String artifactId) {
        NodeList deps = dependencies.getElementsByTagName("dependency");
        for (int i = 0; i < deps.getLength(); i++) {
            Element dep = (Element) deps.item(i);
            // Must be a direct child of <dependencies>, not a nested <dependency> elsewhere.
            if (dep.getParentNode() != dependencies) continue;
            String g = textOf(firstChildElement(dep, "groupId"));
            String a = textOf(firstChildElement(dep, "artifactId"));
            if (groupId.equals(g) && artifactId.equals(a)) return dep;
        }
        return null;
    }

    private static Element firstChildElement(Element parent, String name) {
        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(name)) {
                return (Element) n;
            }
        }
        return null;
    }

    private static String textOf(Element el) {
        if (el == null) return null;
        String t = el.getTextContent();
        return t == null ? null : t.trim();
    }

    private static void setText(Element el, String text) {
        // Preserve any element-internal whitespace by only replacing the text content of
        // the first text node. If there is none, set element text directly.
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.TEXT_NODE && !n.getTextContent().isBlank()) {
                n.setNodeValue(text);
                return;
            }
        }
        el.setTextContent(text);
    }

    private static boolean isPropertyRef(String text) {
        return text != null && text.startsWith("${") && text.endsWith("}");
    }

    private static String unwrapProperty(String text) {
        return text.substring(2, text.length() - 1);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private Document read(Path pomPath) {
        try {
            DocumentBuilder builder = newBuilder();
            return builder.parse(pomPath.toFile());
        } catch (Exception e) {
            throw new PomFixException("Failed to parse " + pomPath + ": " + e.getMessage(), e);
        }
    }

    private Document readString(String xml) {
        try {
            DocumentBuilder builder = newBuilder();
            return builder.parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new PomFixException("Failed to parse pom string: " + e.getMessage(), e);
        }
    }

    private DocumentBuilder newBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringComments(false);
        factory.setIgnoringElementContentWhitespace(false);
        // Harden against XXE — these are typed pom files but defense-in-depth is cheap.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder();
    }

    private void write(Document doc, Path pomPath) {
        try {
            String out = writeString(doc);
            Files.writeString(pomPath, out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new PomFixException("Failed to write " + pomPath + ": " + e.getMessage(), e);
        }
    }

    private String writeString(Document doc) {
        try {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter sw = new StringWriter();
            t.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new PomFixException("Failed to serialize pom: " + e.getMessage(), e);
        }
    }
}
