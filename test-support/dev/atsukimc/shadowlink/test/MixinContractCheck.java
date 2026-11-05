package dev.atsukimc.shadowlink.test;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

/** Checks the shipped refmap against real intermediary bytecode without loading game classes. */
public final class MixinContractCheck {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final Gson JSON = new Gson();

    public static void main(String[] args) throws Exception {
        require(args.length == 2, "Expected the mod JAR and its intermediary Minecraft JAR");
        try (JarFile mod = new JarFile(args[0]); JarFile game = new JarFile(args[1])) {
            JsonObject config = readJson(mod, "shadowlink.mixins.json");
            JsonObject refmap = readJson(mod, config.get("refmap").getAsString());
            JsonObject mappings = refmap.getAsJsonObject("mappings");
            require(mappings != null && !mappings.entrySet().isEmpty(), "Empty or missing refmap mappings");
            JsonObject namespaces = refmap.getAsJsonObject("data");
            require(namespaces != null && mappings.equals(namespaces.get("named:intermediary")),
                    "Default and named:intermediary refmap mappings differ");
            int members = verify(mod, game, config, mappings);
            rejectBrokenMappings(mod, game, config, mappings);
            System.out.println("Mixin contracts verified: " + config.getAsJsonArray("mixins").size()
                    + " mixins, " + members + " members; missing mapping and invalid descriptor rejected");
        }
    }

    private static int verify(JarFile mod, JarFile game, JsonObject config, JsonObject mappings) throws Exception {
        require(config.get("required").getAsBoolean(), "Mixin failures must remain fatal");
        require(config.getAsJsonObject("injectors").get("defaultRequire").getAsInt() >= 1,
                "Mixin injections must require a match");
        int members = 0;
        for (JsonElement name : config.getAsJsonArray("mixins")) {
            String path = config.get("package").getAsString().replace('.', '/') + "/" + name.getAsString();
            ClassNode mixin = readClass(mod, path);
            JsonObject references = mappings.getAsJsonObject(path);
            require(references != null, path + ": missing refmap section");
            AnnotationNode annotation = annotation(mixin.invisibleAnnotations, MIXIN);
            require(annotation != null, path + ": missing @Mixin");
            String targetName = target(annotation, references);
            ClassNode target = readClass(game, targetName);
            int checked = verifyMembers(mixin, target, references);
            require(checked > 0, path + ": no checked members");
            members += checked;
        }
        require(members > 0, "No mixin contracts checked");
        return members;
    }

    private static int verifyMembers(ClassNode mixin, ClassNode target, JsonObject references) {
        int count = 0;
        for (FieldNode field : mixin.fields) {
            if (annotation(field.visibleAnnotations, SHADOW) != null
                    || annotation(field.invisibleAnnotations, SHADOW) != null) {
                require(target.fields.stream().anyMatch(candidate -> candidate.name.equals(field.name)
                                && candidate.desc.equals(field.desc)),
                        mixin.name + ": missing shadow field " + field.name + ":" + field.desc);
                count++;
            }
        }
        for (MethodNode method : mixin.methods) {
            AnnotationNode inject = annotation(method.visibleAnnotations, INJECT);
            AnnotationNode accessor = annotation(method.visibleAnnotations, ACCESSOR);
            AnnotationNode invoker = annotation(method.visibleAnnotations, INVOKER);
            if (inject != null) {
                List<String> selectors = list(value(inject, "method"));
                require(!selectors.isEmpty(), mixin.name + ": empty injection selector");
                Object required = value(inject, "require");
                require(required == null || ((Integer) required) != 0, "Optional injection: " + method.name);
                for (String selector : selectors) {
                    MethodNode destination = findMethod(target, reference(references, selector));
                    verifyCallback(method, destination);
                    verifyInjectionPoint(inject, destination);
                    count++;
                }
            } else if (accessor != null) {
                String field = reference(references, (String) value(accessor, "value"));
                int separator = field.indexOf(':');
                require(separator > 0, "Invalid field reference: " + field);
                String name = field.substring(0, separator);
                String descriptor = field.substring(separator + 1);
                require(target.fields.stream().anyMatch(candidate -> candidate.name.equals(name)
                                && candidate.desc.equals(descriptor)),
                        mixin.name + ": missing accessor target " + field);
                require(method.desc.equals("()" + descriptor), "Getter descriptor mismatch: " + method.name);
                count++;
            } else if (invoker != null) {
                MethodNode destination = findMethod(target, reference(references, (String) value(invoker, "value")));
                require(method.desc.equals(destination.desc), "Invoker descriptor mismatch: " + method.name);
                count++;
            }
        }
        return count;
    }

    private static void verifyCallback(MethodNode callback, MethodNode destination) {
        List<Type> arguments = new ArrayList<>(Arrays.asList(Type.getArgumentTypes(destination.desc)));
        String callbackType = Type.getReturnType(destination.desc).equals(Type.VOID_TYPE)
                ? "CallbackInfo" : "CallbackInfoReturnable";
        arguments.add(Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/" + callbackType));
        String descriptor = Type.getMethodDescriptor(Type.VOID_TYPE, arguments.toArray(new Type[0]));
        require(callback.desc.equals(descriptor), "Callback descriptor mismatch: " + callback.name
                + " expected " + descriptor + " got " + callback.desc);
        require((callback.access & Opcodes.ACC_STATIC) == (destination.access & Opcodes.ACC_STATIC),
                "Static callback mismatch: " + callback.name);
    }

    private static void verifyInjectionPoint(AnnotationNode inject, MethodNode destination) {
        require((destination.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0,
                "Injection target has no body: " + destination.name);
        List<AnnotationNode> points = list(value(inject, "at"));
        require(!points.isEmpty(), "Missing injection point");
        for (AnnotationNode point : points) {
            String location = (String) value(point, "value");
            // New kinds of injection must get their own validation before the gate accepts them.
            require("HEAD".equals(location) || "TAIL".equals(location), "Unchecked injection point: " + location);
            require(point.values.size() == 2, "Unchecked injection point options: " + point.values);
            if ("TAIL".equals(location)) {
                boolean returns = false;
                for (org.objectweb.asm.tree.AbstractInsnNode instruction : destination.instructions) {
                    int opcode = instruction.getOpcode();
                    if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) returns = true;
                }
                require(returns, "TAIL target has no return: " + destination.name);
            }
        }
    }

    private static MethodNode findMethod(ClassNode target, String reference) {
        if (reference.startsWith("L")) {
            int ownerEnd = reference.indexOf(';');
            require(ownerEnd > 1 && target.name.equals(reference.substring(1, ownerEnd)),
                    "Method owner mismatch: " + reference + " vs " + target.name);
            reference = reference.substring(ownerEnd + 1);
        }
        int descriptorStart = reference.indexOf('(');
        require(descriptorStart > 0, "Missing method descriptor: " + reference);
        String name = reference.substring(0, descriptorStart);
        String descriptor = reference.substring(descriptorStart);
        for (MethodNode method : target.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
        }
        throw new IllegalStateException("Missing runtime method: " + target.name + "." + reference);
    }

    private static String target(AnnotationNode mixin, JsonObject references) {
        List<Type> classes = list(value(mixin, "value"));
        if (!classes.isEmpty()) {
            require(classes.size() == 1, "Multiple mixin targets need explicit validation");
            return classes.get(0).getInternalName();
        }
        List<String> classesByName = list(value(mixin, "targets"));
        require(classesByName.size() == 1, "Expected exactly one mixin target");
        return reference(references, classesByName.get(0));
    }

    private static String reference(JsonObject mappings, String selector) {
        require(selector != null && mappings.has(selector), "Missing refmap entry: " + selector);
        return mappings.get(selector).getAsString();
    }

    private static void rejectBrokenMappings(JarFile mod, JarFile game, JsonObject config, JsonObject mappings) throws Exception {
        Map.Entry<String, JsonElement> mixin = mappings.entrySet().iterator().next();
        String selector = mixin.getValue().getAsJsonObject().entrySet().iterator().next().getKey();
        JsonObject missing = JSON.fromJson(JSON.toJson(mappings), JsonObject.class);
        missing.getAsJsonObject(mixin.getKey()).remove(selector);
        expectRejected(mod, game, config, missing);
        for (Map.Entry<String, JsonElement> section : mappings.entrySet()) {
            for (Map.Entry<String, JsonElement> entry : section.getValue().getAsJsonObject().entrySet()) {
                String mapped = entry.getValue().getAsString();
                int descriptor = mapped.indexOf('(');
                if (descriptor > 0) {
                    JsonObject invalid = JSON.fromJson(JSON.toJson(mappings), JsonObject.class);
                    invalid.getAsJsonObject(section.getKey()).addProperty(entry.getKey(), mapped.substring(0, descriptor) + "(J)V");
                    expectRejected(mod, game, config, invalid);
                    return;
                }
            }
        }
        throw new AssertionError("No method descriptor available for negative check");
    }

    private static void expectRejected(JarFile mod, JarFile game, JsonObject config, JsonObject mappings) throws Exception {
        try {
            verify(mod, game, config, mappings);
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Invalid refmap was accepted");
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations != null) for (AnnotationNode annotation : annotations) {
            if (annotation.desc.equals(descriptor)) return annotation;
        }
        return null;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> list(Object value) {
        return value == null ? java.util.Collections.emptyList() : (List<T>) value;
    }

    private static ClassNode readClass(JarFile jar, String name) throws Exception {
        require(jar.getJarEntry(name + ".class") != null, "Missing class in " + jar.getName() + ": " + name);
        try (InputStream input = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static JsonObject readJson(JarFile jar, String name) throws Exception {
        require(jar.getJarEntry(name) != null, "Missing JSON in " + jar.getName() + ": " + name);
        try (InputStream input = jar.getInputStream(jar.getJarEntry(name));
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JSON.fromJson(reader, JsonObject.class);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
