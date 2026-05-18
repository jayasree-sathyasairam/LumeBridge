package com.lumebridge.payload;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Deterministic XML: sorted attributes; sibling elements sorted by canonical subtree fingerprint. */
public final class XmlCanonicalNormalizer {

    private XmlCanonicalNormalizer() {}

    public static byte[] normalizeUtf8(byte[] raw) throws Exception {
        DocumentBuilderFactory df = DocumentBuilderFactory.newInstance();
        df.setNamespaceAware(true);
        df.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        try {
            df.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            df.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // Some parsers ignore unsupported attributes
        }
        df.setExpandEntityReferences(false);
        Document doc = df.newDocumentBuilder().parse(new ByteArrayInputStream(raw));
        Element root = doc.getDocumentElement();
        if (root == null) {
            return raw;
        }
        Map<Element, String> sig = new IdentityHashMap<>();
        return emitElement(root, sig).getBytes(StandardCharsets.UTF_8);
    }

    private static List<Element> childElements(Element parent) {
        NodeList nl = parent.getChildNodes();
        List<Element> out = new ArrayList<>();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n instanceof Element el) {
                out.add(el);
            }
        }
        return out;
    }

    private static String subtreeSignature(Element e, Map<Element, String> sig) {
        return sig.computeIfAbsent(e, x -> {
            TreeMap<String, String> attrs = sortedAttrs(x);
            StringBuilder sb = new StringBuilder();
            sb.append(x.getTagName());
            attrs.forEach((k, v) -> sb.append('|').append(k).append('=').append(v));
            List<Element> kids = childElements(x);
            kids.sort(Comparator.comparing(ch -> subtreeSignature(ch, sig)));
            for (Element ch : kids) {
                sb.append(';').append(subtreeSignature(ch, sig));
            }
            return sb.toString();
        });
    }

    private static TreeMap<String, String> sortedAttrs(Element e) {
        TreeMap<String, String> m = new TreeMap<>();
        NamedNodeMap nn = e.getAttributes();
        for (int i = 0; i < nn.getLength(); i++) {
            Node n = nn.item(i);
            if (n instanceof Attr a) {
                m.put(a.getName(), a.getValue());
            }
        }
        return m;
    }

    private static String emitElement(Element e, Map<Element, String> sig) {
        StringBuilder sb = new StringBuilder();
        sb.append('<').append(e.getTagName());
        TreeMap<String, String> attrs = sortedAttrs(e);
        for (Map.Entry<String, String> en : attrs.entrySet()) {
            sb.append(' ').append(en.getKey()).append("=\"").append(xmlEscape(en.getValue())).append('"');
        }
        sb.append('>');
        List<Element> kids = childElements(e);
        kids.sort(Comparator.comparing(ch -> subtreeSignature(ch, sig)));
        for (Element ch : kids) {
            sb.append(emitElement(ch, sig));
        }
        sb.append("</").append(e.getTagName()).append('>');
        return sb.toString();
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
