package me.mkgl.yaml2jcr;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.jcr.NamespaceRegistry.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.jackrabbit.util.ISO9075;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.output.support.AbstractXMLOutputProcessor;
import org.jdom2.output.support.FormatStack;
import org.yaml.snakeyaml.Yaml;

/**
 * This class converts arbitrary YAML files into JCR Document View export files, ready to import into any JCR-compliant repository.
 *
 * @see <a href="https://docs.adobe.com/content/docs/en/spec/jcr/2.0/7_Export.html#7.3%20Document%20View">7.3 Document View</a>
 */
public class Yaml2Jcr {

    public static void main(String[] args) throws Exception {
        Path parent = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        parent = parent.toAbsolutePath();

        Path dest = args.length > 1 ? Paths.get(args[1]) : Paths.get(".");
        dest = dest.toAbsolutePath();

        validateDirectory(parent);
        validateDirectory(dest);

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(parent, "*.yaml")) {
            for (Path path : ds) {
                convertToJcrDocumentView(path, dest);
            }
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: ");
            e.printStackTrace();
            System.exit(-1);
        }

        System.exit(0);
    }

    private static void validateDirectory(Path parent) {
        if (!Files.exists(parent)) {
            System.out.println("Path does not exist: " + parent.toString());
            System.exit(1);
        } else if (!Files.isDirectory(parent)) {
            System.out.println("Path must be a directory: " + parent.toString());
            System.exit(2);
        }
    }

    private static void convertToJcrDocumentView(Path path, Path dest) throws Exception {
        BufferedReader reader = Files.newBufferedReader(path, UTF_8);

        Map<?, ?> map = (Map<?, ?>) new Yaml().load(reader);

        String defName = path.getFileName().toString();
        defName = defName.substring(0, defName.indexOf(".yaml"));

        Element root = new Element(defName);
        Document document = new Document(root);
        buildElement(map, root);

        XMLOutputter xmlOutput = new XMLOutputter();
        xmlOutput.setXMLOutputProcessor(new AbstractXMLOutputProcessor() {

            @Override
            protected void printAttribute(Writer out, FormatStack fstack, Attribute attribute) throws IOException {
                write(out, fstack.getLineSeparator());
                write(out, fstack.getLevelIndent() + " ");
                super.printAttribute(out, fstack, attribute);
            }
        });
        Format format = Format.getPrettyFormat();
        xmlOutput.setFormat(format);

        Path xmlPath = dest.resolve("config.modules.samples.templates.pages." + defName + ".xml");
        if (!Files.exists(xmlPath)) {
            Files.createFile(xmlPath);
        }
        BufferedWriter writer = Files.newBufferedWriter(xmlPath, UTF_8);

        xmlOutput.output(document, writer);
        System.out.println("Successfully converted " + path.getFileName() + " into " + xmlPath.getFileName());
    }

    private static void buildElement(Map<?, ?> map, Element parent) {
        parent.setAttribute("primaryType", "mgnl:contentNode", Namespace.getNamespace(PREFIX_JCR, NAMESPACE_JCR));
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value != null && Map.class.isAssignableFrom(value.getClass())) {
                // xml elements cannot start with digits
                key = ISO9075.encode(key);
                Element content = new Element(key);
                buildElement((Map<?, ?>) value, content);
                parent.addContent(content);
            } else {
                parent.setAttribute(key, String.valueOf(value));
            }
        }
    }
}
