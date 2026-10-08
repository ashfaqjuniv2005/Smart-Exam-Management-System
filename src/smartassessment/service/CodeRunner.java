package smartassessment.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Compiles and runs the student's code on the local machine. */
public final class CodeRunner {
    private static final int TIME_LIMIT_SECONDS = 10;
    private static final int MAX_OUTPUT = 20000;

    private CodeRunner() { }

    /** Runs the code and returns everything that should be shown in the output console. */
    public static String run(String language, String code, String stdin) {
        File dir = null;
        try {
            dir = Files.createTempDirectory("smartexam_").toFile();
            if ("Java".equals(language)) {
                return runJava(dir, code, stdin);
            }
            return runNative(dir, language, code, stdin);
        } catch (Exception ex) {
            return "Error: " + ex.getMessage();
        } finally {
            if (dir != null) deleteRecursively(dir);
        }
    }

    private static String runJava(File dir, String code, String stdin) throws Exception {
        String cls = findJavaClassName(code);
        File src = new File(dir, cls + ".java");
        Files.write(src.toPath(), code.getBytes(StandardCharsets.UTF_8));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return "Java compiler not found.\nPlease start this application using a JDK (not a JRE).";
        }
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int rc = compiler.run(null, null, err, "-d", dir.getPath(), src.getPath());
        if (rc != 0) {
            return "Compilation failed:\n" + err.toString("UTF-8");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExecutable());
        cmd.add("-cp");
        cmd.add(dir.getPath());
        cmd.add(cls);
        return execute(cmd, dir, stdin, "--- Program output ---\n");
    }

    private static String runNative(File dir, String language, String code, String stdin) throws Exception {
        boolean cpp = "C++".equals(language);
        File src = new File(dir, cpp ? "main.cpp" : "main.c");
        File exe = new File(dir, "program.exe");
        Files.write(src.toPath(), code.getBytes(StandardCharsets.UTF_8));

        List<String> compile = new ArrayList<>();
        compile.add(cpp ? "g++" : "gcc");
        compile.add(src.getPath());
        compile.add("-o");
        compile.add(exe.getPath());
        String compileOut;
        try {
            compileOut = execute(compile, dir, "", "");
        } catch (IOException ex) {
            return (cpp ? "g++" : "gcc") + " compiler was not found on this computer.";
        }
        if (!exe.exists()) {
            return "Compilation failed:\n" + compileOut;
        }
        List<String> run = new ArrayList<>();
        run.add(exe.getPath());
        String warnings = compileOut.trim().isEmpty() ? "" : "Compiler messages:\n" + compileOut + "\n";
        return warnings + execute(run, dir, stdin, "--- Program output ---\n");
    }

    private static String execute(List<String> cmd, File dir, String stdin, String header) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        final StringBuilder out = new StringBuilder();
        final InputStream is = p.getInputStream();
        Thread reader = new Thread(() -> {
            try {
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) {
                    synchronized (out) {
                        if (out.length() < MAX_OUTPUT) {
                            out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                        }
                    }
                }
            } catch (IOException ignored) { }
        });
        reader.setDaemon(true);
        reader.start();

        try (OutputStream os = p.getOutputStream()) {
            if (stdin != null && !stdin.isEmpty()) {
                os.write(stdin.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (IOException ignored) { }

        boolean finished = p.waitFor(TIME_LIMIT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
        }
        reader.join(1000);
        String text;
        synchronized (out) {
            text = out.toString();
        }
        if (text.length() >= MAX_OUTPUT) text += "\n[Output truncated]";
        if (!finished) text += "\n[Time limit exceeded: stopped after " + TIME_LIMIT_SECONDS + " seconds]";
        else if (header.length() > 0) text += "\n[Process finished with exit code " + p.exitValue() + "]";
        return header + text;
    }

    static String findJavaClassName(String code) {
        Matcher m = Pattern.compile("public\\s+(?:final\\s+|abstract\\s+)*class\\s+(\\w+)").matcher(code);
        if (m.find()) return m.group(1);
        m = Pattern.compile("class\\s+(\\w+)[^{]*\\{[^}]*static\\s+void\\s+main", Pattern.DOTALL).matcher(code);
        if (m.find()) return m.group(1);
        return "Main";
    }

    private static String javaExecutable() {
        File bin = new File(System.getProperty("java.home"), "bin");
        File exe = new File(bin, "java.exe");
        if (exe.exists()) return exe.getPath();
        File unix = new File(bin, "java");
        return unix.exists() ? unix.getPath() : "java";
    }

    private static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursively(c);
        }
        f.delete();
    }
}
