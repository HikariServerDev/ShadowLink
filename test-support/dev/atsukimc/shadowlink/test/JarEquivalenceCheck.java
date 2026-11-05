package dev.atsukimc.shadowlink.test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Migration gate: preserve executable classes, mixins, NBT logic and runtime resources. */
public final class JarEquivalenceCheck {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && "--snapshot".equals(args[0])) {
            Files.write(Paths.get(args[2]), JSON.toJson(snapshot(args[1])).getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (args.length != 3) throw new IllegalArgumentException("Expected baseline JSON, Minecraft version, mod JAR");
        JsonObject baseline = JSON.fromJson(new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8), JsonObject.class);
        JsonObject expected = baseline.getAsJsonObject("targets").getAsJsonObject(args[1]).getAsJsonObject("jar");
        JsonObject actual = snapshot(args[2]);
        for (String section : new String[] {"classes", "resources"}) {
            if (!expected.get(section).equals(actual.get(section))) {
                throw new IllegalStateException("Migration changed " + section + " for Minecraft " + args[1]
                        + ": " + differences(expected.getAsJsonObject(section), actual.getAsJsonObject(section)));
            }
        }
        System.out.println("Migration JAR equivalence verified: " + args[1] + ", "
                + actual.getAsJsonObject("classes").entrySet().size() + " classes; only debug/ZIP data and mod version ignored");
    }

    private static JsonObject snapshot(String filename) throws Exception {
        Map<String, String> classes = new TreeMap<>();
        Map<String, String> resources = new TreeMap<>();
        try (ZipFile jar = new ZipFile(filename)) {
            java.util.Enumeration<? extends ZipEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                byte[] content;
                try (InputStream input = jar.getInputStream(entry)) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] bytes = new byte[8192];
                    int count;
                    while ((count = input.read(bytes)) != -1) buffer.write(bytes, 0, count);
                    content = buffer.toByteArray();
                }
                if (entry.getName().endsWith(".class")) {
                    // Rebuild the constant pool as well: adding comments changes debug tables
                    // and their pool indices, without changing any executable instruction.
                    ClassWriter writer = new ClassWriter(0);
                    new ClassReader(content).accept(writer, ClassReader.SKIP_DEBUG);
                    classes.put(entry.getName(), digest(writer.toByteArray()));
                } else {
                    if (entry.getName().endsWith(".json")) {
                        JsonElement json = JSON.fromJson(new String(content, StandardCharsets.UTF_8), JsonElement.class);
                        if ("fabric.mod.json".equals(entry.getName())) json.getAsJsonObject().remove("version");
                        content = JSON.toJson(sortJson(json)).getBytes(StandardCharsets.UTF_8);
                    }
                    resources.put(entry.getName(), digest(content));
                }
            }
        }
        JsonObject result = new JsonObject();
        result.add("classes", JSON.toJsonTree(classes));
        result.add("resources", JSON.toJsonTree(resources));
        return result;
    }

    private static JsonElement sortJson(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            Map<String, JsonElement> sorted = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) sorted.put(entry.getKey(), entry.getValue());
            for (Map.Entry<String, JsonElement> entry : sorted.entrySet()) result.add(entry.getKey(), sortJson(entry.getValue()));
            return result;
        }
        if (value.isJsonArray()) {
            com.google.gson.JsonArray result = new com.google.gson.JsonArray();
            for (JsonElement element : value.getAsJsonArray()) result.add(sortJson(element));
            return result;
        }
        return value;
    }

    private static String differences(JsonObject expected, JsonObject actual) {
        Map<String, Boolean> names = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : expected.entrySet()) names.put(entry.getKey(), true);
        for (Map.Entry<String, JsonElement> entry : actual.entrySet()) names.put(entry.getKey(), true);
        java.util.List<String> changed = new java.util.ArrayList<>();
        for (String name : names.keySet()) if (!java.util.Objects.equals(expected.get(name), actual.get(name))) changed.add(name);
        return changed.toString();
    }

    private static String digest(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format("%02x", value & 255));
        return result.toString();
    }
}
