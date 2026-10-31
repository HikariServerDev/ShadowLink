package dev.atsukimc.shadowlink.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

/**
 * Loads the core checks without starting Minecraft or applying mixins. Named Minecraft JARs
 * need package access widened after remapping (as Fabric normally does), because obfuscated
 * classes originally shared a package. Only the in-memory test classes are transformed.
 */
public final class OfflineLauncher extends ClassLoader {
    private OfflineLauncher() {
        super(OfflineLauncher.class.getClassLoader());
    }

    public static void main(String[] args) throws Throwable {
        ClassLoader loader = new OfflineLauncher();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            Class.forName("dev.atsukimc.shadowlink.core.ShadowRegression", true, loader)
                    .getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!name.startsWith("net.minecraft.") && !name.startsWith("dev.atsukimc.shadowlink.core.")) {
            return super.loadClass(name, resolve);
        }
        Class<?> loaded = findLoadedClass(name);
        if (loaded == null) {
            try (InputStream input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (input == null) throw new ClassNotFoundException(name);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
                byte[] bytes = output.toByteArray();
                if (name.startsWith("net.minecraft.")) bytes = widenPackageAccess(bytes);
                loaded = defineClass(name, bytes, 0, bytes.length);
            } catch (java.io.IOException error) {
                throw new ClassNotFoundException(name, error);
            }
        }
        if (resolve) resolveClass(loaded);
        return loaded;
    }

    private static byte[] widenPackageAccess(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            private int widen(int access) {
                return (access & Opcodes.ACC_PRIVATE) != 0 ? access
                        : (access & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
            }

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, widen(access), name, signature, superName, interfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                return super.visitField(widen(access), name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return super.visitMethod(widen(access), name, descriptor, signature, exceptions);
            }
        }, 0);
        return writer.toByteArray();
    }
}
