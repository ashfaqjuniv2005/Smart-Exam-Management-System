package smartassessment.service;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import smartassessment.model.AssessmentResult;
import smartassessment.model.Exam;
import smartassessment.model.Submission;

/**
 * Stores exams, submissions and results as plain files using Java object serialization.
 * (No SQL / database is used.)
 */
public class ExamRepository {
    private final File base;

    public ExamRepository() {
        this(new File(System.getProperty("user.dir"), "SmartExamData"));
    }

    public ExamRepository(File base) { this.base = base; }

    public File getBaseDir() { return base; }

    private static String safe(String s) {
        return s == null ? "unknown" : s.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private File examsDir() {
        File d = new File(base, "exams");
        d.mkdirs();
        return d;
    }

    private File submissionDir(String examCode) {
        File d = new File(new File(base, "submissions"), safe(examCode));
        d.mkdirs();
        return d;
    }

    // ---------------------------------------------------------------- exams
    public void saveExam(Exam exam) throws IOException {
        File f = new File(examsDir(), safe(exam.getCode()) + ".exam");
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(f))) {
            out.writeObject(exam);
        }
    }

    public Exam loadExam(String code) throws IOException, ClassNotFoundException {
        File f = new File(examsDir(), safe(code) + ".exam");
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            return (Exam) in.readObject();
        }
    }

    public List<String> listExamCodes() {
        List<String> codes = new ArrayList<>();
        File[] files = examsDir().listFiles((d, n) -> n.endsWith(".exam"));
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                codes.add(n.substring(0, n.length() - ".exam".length()));
            }
        }
        Collections.sort(codes);
        return codes;
    }

    // ---------------------------------------------------------- submissions
    public void saveSubmission(Submission s) throws IOException {
        File dir = submissionDir(s.getExamCode());
        String name = safe(s.getStudent().getRoll());
        try (ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(new File(dir, name + ".sub")))) {
            out.writeObject(s);
        }
        // Readable copies of the code answers, so the teacher can also open them in an IDE.
        for (int i = 0; i < s.getCodeAnswers().size(); i++) {
            String code = s.getCodeAnswers().get(i);
            if (code == null || code.trim().isEmpty()) continue;
            String lang = i < s.getLanguages().size() ? s.getLanguages().get(i) : "Java";
            File codeFile = new File(dir, name + "_" + safe(s.getStudent().getName())
                    + "_Q" + (i + 1) + extensionFor(lang));
            Files.write(codeFile.toPath(), code.getBytes(StandardCharsets.UTF_8));
        }
    }

    public List<Submission> loadSubmissions(String examCode) {
        List<Submission> list = new ArrayList<>();
        File[] files = submissionDir(examCode).listFiles((d, n) -> n.endsWith(".sub"));
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
                    list.add((Submission) in.readObject());
                } catch (Exception ex) {
                    System.err.println("Could not read " + f + ": " + ex);
                }
            }
        }
        return list;
    }

    /** True if this roll already has a saved submission for the exam (even from an earlier sitting). */
    public boolean hasSubmission(String examCode, String roll) {
        return new File(submissionDir(examCode), safe(roll) + ".sub").exists();
    }

    // -------------------------------------------------------------- results
    public void saveResult(AssessmentResult r) throws IOException {
        File f = new File(submissionDir(r.getExamCode()), safe(r.getStudent().getRoll()) + ".res");
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(f))) {
            out.writeObject(r);
        }
    }

    /** Returns the stored result or null when there is none. */
    public AssessmentResult loadResult(String examCode, String roll) {
        File f = new File(submissionDir(examCode), safe(roll) + ".res");
        if (!f.exists()) return null;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            return (AssessmentResult) in.readObject();
        } catch (Exception ex) {
            return null;
        }
    }

    // ---------------------------------------------------------------- misc
    public static String extensionFor(String language) {
        if ("C++".equals(language)) return ".cpp";
        if ("C".equals(language)) return ".c";
        return ".java";
    }

    public void writeCsv(File target, List<String[]> rows) throws IOException {
        try (PrintWriter pw = new PrintWriter(target, "UTF-8")) {
            for (String[] row : rows) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append('"').append(row[i] == null ? "" : row[i].replace("\"", "\"\"")).append('"');
                }
                pw.println(sb);
            }
        }
    }

    // ------------------------------------------------------- question files
    /** Reads a question file (.txt / .csv as text, .pdf by extracting its text). */
    public String readQuestionFile(File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());
        if (file.getName().toLowerCase().endsWith(".pdf")) {
            return PdfText.extract(data);
        }
        String text = new String(data, StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    public void writeText(File target, String text) throws IOException {
        Files.write(target.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Small PDF text reader that needs no library: it understands compressed streams,
     * object streams and ToUnicode font maps. It works for normal text PDFs
     * (Word, LibreOffice, browsers ...) but not for scanned pictures or password-protected files.
     */
    private static final class PdfText {
        private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;
        private static final Pattern REF = Pattern.compile("(\\d+)\\s+\\d+\\s+R");

        /** A font: how the bytes of a text string are turned into characters. */
        private static final class Font {
            final Map<Integer, String> map = new HashMap<>();
            int codeLen = 1;
            boolean type0;
        }

        private final Map<Integer, String> dicts = new HashMap<>();
        private final Map<Integer, byte[]> streams = new HashMap<>();
        private final Map<Integer, Font> fontCache = new HashMap<>();
        private int unreadable;

        static String extract(byte[] data) throws IOException {
            return new PdfText().run(data);
        }

        private String run(byte[] data) throws IOException {
            String src = new String(data, LATIN1);
            if (src.contains("/Encrypt")) throw new IOException("This PDF is password protected.");
            readObjects(src);
            readObjectStreams();
            StringBuilder out = new StringBuilder();
            for (int page : pageList()) {
                Map<String, Font> fonts = fontsOf(resourcesOf(page));
                StringBuilder content = new StringBuilder();
                for (int n : contentRefs(dicts.get(page))) {
                    byte[] s = streams.get(n);
                    if (s != null) content.append(new String(s, LATIN1)).append('\n');
                }
                parseContent(content.toString(), fonts, out);
                newline(out);
                out.append('\n');
            }
            String text = out.toString();
            int letters = 0;
            for (int i = 0; i < text.length(); i++) if (Character.isLetter(text.charAt(i))) letters++;
            if (letters < 20 || unreadable > letters) {
                throw new IOException("No readable text was found in this PDF (it may be a scanned picture or use "
                        + "special fonts). Please save the questions as a .txt or .csv file instead.");
            }
            return text;
        }

        // ------------------------------------------------------------ objects
        private void readObjects(String src) {
            Matcher m = Pattern.compile("(\\d+)\\s+(\\d+)\\s+obj\\b").matcher(src);
            int pos = 0;
            while (m.find(pos)) {
                int num = Integer.parseInt(m.group(1));
                int start = m.end();
                int end = src.indexOf("endobj", start);
                if (end < 0) end = src.length();
                String body = src.substring(start, end);
                int s = body.indexOf("stream");
                if (s >= 0 && body.substring(0, s).trim().endsWith(">>")) {
                    String dict = body.substring(0, s);
                    int ds = s + 6;
                    if (body.startsWith("\r\n", ds)) ds += 2;
                    else if (ds < body.length() && (body.charAt(ds) == '\n' || body.charAt(ds) == '\r')) ds++;
                    int de = body.lastIndexOf("endstream");
                    if (de < ds) de = body.length();
                    dicts.put(num, dict);
                    byte[] decoded = decode(dict, body.substring(ds, de).getBytes(LATIN1));
                    if (decoded != null) streams.put(num, decoded);
                } else {
                    dicts.put(num, body);
                }
                pos = Math.max(end + 6, m.end());
            }
        }

        private static byte[] decode(String dict, byte[] raw) {
            if (dict.matches("(?s).*/(LZWDecode|RunLengthDecode|DCTDecode|JPXDecode|CCITTFaxDecode).*")) {
                return null; // pictures and rarely used encodings: no text inside
            }
            byte[] data = raw;
            // some generators (for example reportlab) write /Filter [/ASCII85Decode /FlateDecode]
            if (dict.contains("/ASCII85Decode")) data = ascii85(data);
            else if (dict.contains("/ASCIIHexDecode")) data = asciiHex(data);
            if (!dict.contains("/FlateDecode")) return data;
            Inflater inf = new Inflater();
            inf.setInput(data);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            try {
                while (!inf.finished()) {
                    int n = inf.inflate(buf);
                    if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                    bos.write(buf, 0, n);
                }
            } catch (DataFormatException ex) {
                // keep what could be read
            } finally {
                inf.end();
            }
            return bos.toByteArray();
        }

        private static byte[] ascii85(byte[] in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            long value = 0;
            int count = 0;
            for (int i = 0; i < in.length; i++) {
                int c = in[i] & 0xFF;
                if (c == '~') break;                       // end marker "~>"
                if (c <= ' ') continue;                    // white space
                if (c == 'z' && count == 0) { out.write(0); out.write(0); out.write(0); out.write(0); continue; }
                if (c < '!' || c > 'u') continue;
                value = value * 85 + (c - '!');
                if (++count == 5) {
                    for (int k = 3; k >= 0; k--) out.write((int) (value >> (8 * k)) & 0xFF);
                    value = 0;
                    count = 0;
                }
            }
            if (count > 1) {                               // last, shorter group
                for (int k = count; k < 5; k++) value = value * 85 + 84;
                for (int k = 3; k > 4 - count; k--) out.write((int) (value >> (8 * k)) & 0xFF);
            }
            return out.toByteArray();
        }

        private static byte[] asciiHex(byte[] in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int high = -1;
            for (byte b : in) {
                if (b == '>') break;
                int d = Character.digit(b & 0xFF, 16);
                if (d < 0) continue;
                if (high < 0) high = d;
                else { out.write(high * 16 + d); high = -1; }
            }
            if (high >= 0) out.write(high * 16);
            return out.toByteArray();
        }

        /** PDF 1.5 files store many objects inside "object streams". */
        private void readObjectStreams() {
            for (Integer key : new ArrayList<>(dicts.keySet())) {
                String d = dicts.get(key);
                byte[] data = streams.get(key);
                if (data == null || !Pattern.compile("/Type\\s*/ObjStm").matcher(d).find()) continue;
                Matcher nm = Pattern.compile("/N\\s+(\\d+)").matcher(d);
                Matcher fm = Pattern.compile("/First\\s+(\\d+)").matcher(d);
                if (!nm.find() || !fm.find()) continue;
                int n = Integer.parseInt(nm.group(1));
                int first = Integer.parseInt(fm.group(1));
                String text = new String(data, LATIN1);
                if (first > text.length()) continue;
                String[] head = text.substring(0, first).trim().split("\\s+");
                for (int i = 0; i < n && 2 * i + 1 < head.length; i++) {
                    int num = Integer.parseInt(head[2 * i]);
                    int off = first + Integer.parseInt(head[2 * i + 1]);
                    int next = (2 * i + 3 < head.length) ? first + Integer.parseInt(head[2 * i + 3]) : text.length();
                    if (off <= next && next <= text.length()) dicts.put(num, text.substring(off, next));
                }
            }
        }

        // -------------------------------------------------------------- pages
        private List<Integer> pageList() {
            List<Integer> pages = new ArrayList<>();
            List<Integer> keys = new ArrayList<>(dicts.keySet());
            Collections.sort(keys);
            for (int k : keys) {
                String d = dicts.get(k);
                if (Pattern.compile("/Type\\s*/Catalog").matcher(d).find()) {
                    Matcher m = Pattern.compile("/Pages\\s+(\\d+)\\s+\\d+\\s+R").matcher(d);
                    if (m.find()) walk(Integer.parseInt(m.group(1)), pages, 0);
                    break;
                }
            }
            if (pages.isEmpty()) {
                for (int k : keys) {
                    if (Pattern.compile("/Type\\s*/Page(?![a-z])").matcher(dicts.get(k)).find()) pages.add(k);
                }
            }
            return pages;
        }

        private void walk(int num, List<Integer> pages, int depth) {
            String d = dicts.get(num);
            if (d == null || depth > 40) return;
            if (d.contains("/Kids")) {
                Matcher km = Pattern.compile("/Kids\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(d);
                if (km.find()) {
                    Matcher rm = REF.matcher(km.group(1));
                    while (rm.find()) walk(Integer.parseInt(rm.group(1)), pages, depth + 1);
                }
            } else {
                pages.add(num);
            }
        }

        private String resourcesOf(int page) {
            int cur = page;
            for (int i = 0; i < 30; i++) {
                String d = dicts.get(cur);
                if (d == null) return "";
                if (d.contains("/Resources")) return valueOf(d, "/Resources");
                Matcher m = Pattern.compile("/Parent\\s+(\\d+)\\s+\\d+\\s+R").matcher(d);
                if (!m.find()) return "";
                cur = Integer.parseInt(m.group(1));
            }
            return "";
        }

        private List<Integer> contentRefs(String pageDict) {
            List<Integer> refs = new ArrayList<>();
            if (pageDict == null) return refs;
            int i = pageDict.indexOf("/Contents");
            if (i < 0) return refs;
            String rest = pageDict.substring(i + 9).trim();
            if (rest.startsWith("[")) {
                int e = rest.indexOf(']');
                Matcher m = REF.matcher(rest.substring(0, Math.max(e, 0)));
                while (m.find()) refs.add(Integer.parseInt(m.group(1)));
            } else {
                Matcher m = REF.matcher(rest);
                if (m.find() && m.start() == 0) refs.add(Integer.parseInt(m.group(1)));
            }
            return refs;
        }

        /** Value of a dictionary key: an inline << ... >> dictionary or the text of a referenced object. */
        private String valueOf(String dict, String key) {
            int i = dict.indexOf(key);
            if (i < 0) return "";
            String rest = dict.substring(i + key.length()).trim();
            if (rest.startsWith("<<")) return balanced(rest);
            Matcher m = REF.matcher(rest);
            if (m.find() && m.start() == 0) {
                String d = dicts.get(Integer.parseInt(m.group(1)));
                return d == null ? "" : d;
            }
            return "";
        }

        private static String balanced(String s) {
            int depth = 0;
            for (int i = 0; i + 1 < s.length(); i++) {
                if (s.startsWith("<<", i)) { depth++; i++; }
                else if (s.startsWith(">>", i)) { depth--; i++; if (depth == 0) return s.substring(0, i + 1); }
            }
            return s;
        }

        // -------------------------------------------------------------- fonts
        private Map<String, Font> fontsOf(String resources) {
            Map<String, Font> fonts = new HashMap<>();
            String fd = valueOf(resources, "/Font");
            Matcher m = Pattern.compile("/([^\\s/<>\\[\\]()]+)\\s+(\\d+)\\s+\\d+\\s+R").matcher(fd);
            while (m.find()) fonts.put(m.group(1), loadFont(Integer.parseInt(m.group(2))));
            return fonts;
        }

        private Font loadFont(int num) {
            Font cached = fontCache.get(num);
            if (cached != null) return cached;
            Font f = new Font();
            String d = dicts.get(num);
            if (d != null) {
                f.type0 = d.contains("/Type0");
                if (f.type0) f.codeLen = 2;
                Matcher m = Pattern.compile("/ToUnicode\\s+(\\d+)\\s+\\d+\\s+R").matcher(d);
                if (m.find()) {
                    byte[] cmap = streams.get(Integer.parseInt(m.group(1)));
                    if (cmap != null) parseCMap(new String(cmap, LATIN1), f);
                }
            }
            fontCache.put(num, f);
            return f;
        }

        private static void parseCMap(String text, Font f) {
            Matcher cs = Pattern.compile("begincodespacerange\\s*<([0-9A-Fa-f]+)>").matcher(text);
            if (cs.find()) f.codeLen = Math.max(1, cs.group(1).length() / 2);
            Matcher bc = Pattern.compile("beginbfchar(.*?)endbfchar", Pattern.DOTALL).matcher(text);
            while (bc.find()) {
                Matcher pr = Pattern.compile("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]*)>").matcher(bc.group(1));
                while (pr.find()) {
                    try {
                        f.map.put(Integer.parseInt(pr.group(1), 16), hexToString(pr.group(2)));
                    } catch (NumberFormatException ignored) { }
                }
            }
            Matcher br = Pattern.compile("beginbfrange(.*?)endbfrange", Pattern.DOTALL).matcher(text);
            while (br.find()) {
                Matcher rm = Pattern.compile("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*(<[0-9A-Fa-f]*>|\\[[^\\]]*\\])")
                        .matcher(br.group(1));
                while (rm.find()) {
                    try {
                        int lo = Integer.parseInt(rm.group(1), 16), hi = Integer.parseInt(rm.group(2), 16);
                        String dst = rm.group(3);
                        if (dst.startsWith("[")) {
                            Matcher hm = Pattern.compile("<([0-9A-Fa-f]*)>").matcher(dst);
                            int c = lo;
                            while (hm.find() && c <= hi) f.map.put(c++, hexToString(hm.group(1)));
                        } else {
                            String base = hexToString(dst.substring(1, dst.length() - 1));
                            for (int c = lo; c <= hi && c - lo < 65536; c++) {
                                f.map.put(c, base.isEmpty() ? base
                                        : base.substring(0, base.length() - 1) + (char) (base.charAt(base.length() - 1) + (c - lo)));
                            }
                        }
                    } catch (NumberFormatException ignored) { }
                }
            }
        }

        private static String hexToString(String hex) {
            if (hex.length() % 2 == 1) hex += "0";
            byte[] b = new byte[hex.length() / 2];
            for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
            return new String(b, StandardCharsets.UTF_16BE);
        }

        // ------------------------------------------------------- page content
        private static boolean isDelimiter(char c) {
            return Character.isWhitespace(c) || "()<>[]{}/%".indexOf(c) >= 0;
        }

        private void parseContent(String s, Map<String, Font> fonts, StringBuilder out) {
            List<Object> ops = new ArrayList<>();
            Font cur = null;
            double lastY = Double.NaN;
            int i = 0, n = s.length();
            while (i < n) {
                char c = s.charAt(i);
                if (Character.isWhitespace(c)) { i++; continue; }
                if (c == '%') { while (i < n && s.charAt(i) != '\n' && s.charAt(i) != '\r') i++; continue; }
                if (c == '(') {
                    StringBuilder sb = new StringBuilder();
                    i = readLiteral(s, i, sb);
                    ops.add(sb.toString());
                    continue;
                }
                if (c == '<') {
                    if (i + 1 < n && s.charAt(i + 1) == '<') {   // dictionary: skip it
                        int depth = 0;
                        while (i + 1 < n) {
                            if (s.startsWith("<<", i)) { depth++; i += 2; }
                            else if (s.startsWith(">>", i)) { depth--; i += 2; if (depth == 0) break; }
                            else i++;
                        }
                        continue;
                    }
                    int e = s.indexOf('>', i);
                    if (e < 0) break;
                    StringBuilder sb = new StringBuilder();
                    String hex = s.substring(i + 1, e).replaceAll("\\s+", "");
                    if (hex.length() % 2 == 1) hex += "0";
                    for (int k = 0; k + 1 < hex.length(); k += 2) {
                        try { sb.append((char) Integer.parseInt(hex.substring(k, k + 2), 16)); } catch (NumberFormatException ex) { }
                    }
                    ops.add(sb.toString());
                    i = e + 1;
                    continue;
                }
                if (c == '[') {
                    List<Object> arr = new ArrayList<>();
                    i++;
                    while (i < n && s.charAt(i) != ']') {
                        char d = s.charAt(i);
                        if (d == '(') {
                            StringBuilder sb = new StringBuilder();
                            i = readLiteral(s, i, sb);
                            arr.add(sb.toString());
                        } else if (d == '<') {
                            int e = s.indexOf('>', i);
                            if (e < 0) { i = n; break; }
                            StringBuilder sb = new StringBuilder();
                            String hex = s.substring(i + 1, e).replaceAll("\\s+", "");
                            if (hex.length() % 2 == 1) hex += "0";
                            for (int k = 0; k + 1 < hex.length(); k += 2) {
                                try { sb.append((char) Integer.parseInt(hex.substring(k, k + 2), 16)); } catch (NumberFormatException ex) { }
                            }
                            arr.add(sb.toString());
                            i = e + 1;
                        } else if (Character.isWhitespace(d)) {
                            i++;
                        } else {
                            int st = i;
                            while (i < n && !isDelimiter(s.charAt(i))) i++;
                            if (i == st) { i++; continue; }
                            try { arr.add(Double.valueOf(s.substring(st, i))); } catch (NumberFormatException ex) { }
                        }
                    }
                    i++;
                    ops.add(arr);
                    continue;
                }
                if (c == '/') {
                    int st = i++;
                    while (i < n && !isDelimiter(s.charAt(i))) i++;
                    ops.add(new StringBuilder(s.substring(st, i)));   // a name (StringBuilder marks "name")
                    continue;
                }
                if (c == ']' || c == '>' || c == ')' || c == '{' || c == '}') { i++; continue; }

                int st = i;
                while (i < n && !isDelimiter(s.charAt(i))) i++;
                if (i == st) { i++; continue; }
                String tok = s.substring(st, i);
                if (c == '-' || c == '+' || c == '.' || Character.isDigit(c)) {
                    try { ops.add(Double.valueOf(tok)); continue; } catch (NumberFormatException ex) { /* operator */ }
                }
                // ---- operator
                switch (tok) {
                    case "BT": lastY = Double.NaN; break;
                    case "ET": newline(out); lastY = Double.NaN; break;
                    case "Tf":
                        if (ops.size() >= 2 && ops.get(ops.size() - 2) instanceof StringBuilder) {
                            cur = fonts.get(ops.get(ops.size() - 2).toString().substring(1));
                        }
                        break;
                    case "Tj": if (!ops.isEmpty() && ops.get(ops.size() - 1) instanceof String) decode((String) ops.get(ops.size() - 1), cur, out); break;
                    case "'": newline(out); if (!ops.isEmpty() && ops.get(ops.size() - 1) instanceof String) decode((String) ops.get(ops.size() - 1), cur, out); break;
                    case "\"": newline(out); if (!ops.isEmpty() && ops.get(ops.size() - 1) instanceof String) decode((String) ops.get(ops.size() - 1), cur, out); break;
                    case "TJ":
                        if (!ops.isEmpty() && ops.get(ops.size() - 1) instanceof List) {
                            for (Object o : (List<?>) ops.get(ops.size() - 1)) {
                                if (o instanceof String) decode((String) o, cur, out);
                                else if (o instanceof Double && (Double) o < -250) space(out);
                            }
                        }
                        break;
                    case "Td": case "TD": {
                        double tx = number(ops, 2, 0), ty = number(ops, 1, 0);
                        if (ty != 0) newline(out); else if (tx > 0) space(out);
                        break;
                    }
                    case "T*": newline(out); break;
                    case "Tm": {
                        double y = number(ops, 1, 0);
                        if (!Double.isNaN(lastY)) {
                            if (Math.abs(y - lastY) > 0.5) newline(out); else space(out);
                        }
                        lastY = y;
                        break;
                    }
                    case "BI": {
                        int e = s.indexOf("EI", i);
                        i = e < 0 ? n : e + 2;
                        break;
                    }
                    default: break;
                }
                ops.clear();
            }
        }

        /** The k-th number counted from the end of the operand list (k = 1 is the last). */
        private static double number(List<Object> ops, int fromEnd, double fallback) {
            int idx = ops.size() - fromEnd;
            if (idx >= 0 && idx < ops.size() && ops.get(idx) instanceof Double) return (Double) ops.get(idx);
            return fallback;
        }

        private static int readLiteral(String s, int i, StringBuilder sb) {
            int depth = 0, n = s.length();
            while (i < n) {
                char c = s.charAt(i++);
                if (c == '\\' && i < n) {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case '\r': if (i < n && s.charAt(i) == '\n') i++; break;
                        case '\n': break;
                        default:
                            if (e >= '0' && e <= '7') {
                                int v = e - '0', k = 0;
                                while (k < 2 && i < n && s.charAt(i) >= '0' && s.charAt(i) <= '7') { v = v * 8 + (s.charAt(i++) - '0'); k++; }
                                sb.append((char) (v & 0xFF));
                            } else {
                                sb.append(e);
                            }
                    }
                } else if (c == '(') {
                    if (depth++ > 0) sb.append(c);
                } else if (c == ')') {
                    if (--depth == 0) return i;
                    sb.append(c);
                } else {
                    sb.append(c);
                }
            }
            return i;
        }

        private void decode(String s, Font f, StringBuilder out) {
            if (f == null) f = new Font();
            if (f.map.isEmpty()) {
                if (f.type0) { unreadable += s.length(); return; }
                out.append(new String(s.getBytes(LATIN1), Charset.forName("windows-1252")));
                return;
            }
            for (int i = 0; i + f.codeLen <= s.length(); i += f.codeLen) {
                int code = 0;
                for (int k = 0; k < f.codeLen; k++) code = (code << 8) | (s.charAt(i + k) & 0xFF);
                String t = f.map.get(code);
                if (t != null) out.append(t);
                else if (f.codeLen == 1 && code >= 32) out.append((char) code);
                else unreadable++;
            }
        }

        private static void space(StringBuilder out) {
            int l = out.length();
            if (l > 0 && out.charAt(l - 1) != ' ' && out.charAt(l - 1) != '\n') out.append(' ');
        }

        private static void newline(StringBuilder out) {
            int l = out.length();
            while (l > 0 && out.charAt(l - 1) == ' ') { out.setLength(--l); }
            if (l > 0 && out.charAt(l - 1) != '\n') out.append('\n');
        }
    }
}
