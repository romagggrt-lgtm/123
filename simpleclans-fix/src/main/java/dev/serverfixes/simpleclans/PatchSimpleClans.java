package dev.serverfixes.simpleclans;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

/** Patches exactly one ACF method; all other existing class bytes are copied. */
public final class PatchSimpleClans {
    public static final String VERSION = "2.19.2-05e7abc-locale-fix.1";
    public static final String INPUT_SHA256 = "6a9d79bb69419b2164a15919e28d1f770faf0e07d56191f02a03600c879907c6";
    public static final String ACF = "net/sacredlabyrinth/phaed/simpleclans/acf/";
    public static final String MANAGER = ACF + "BukkitCommandManager";
    public static final String HELPER = ACF + "ClientLocale";
    public static final String METHOD = "(Lorg/bukkit/entity/Player;)V";

    private PatchSimpleClans() {}

    public static byte[] patchManager(byte[] original) {
        var reader = new ClassReader(original);
        var writer = new ClassWriter(reader, 0);
        int[] count = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name.equals("readPlayerLocale") && descriptor.equals(METHOD)) {
                    count[0]++;
                    var mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "read", "(L" + MANAGER + ";Lorg/bukkit/entity/Player;)V", false);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(2, 2);
                    mv.visitEnd();
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, 0);
        if (count[0] != 1) throw new IllegalArgumentException("Expected exactly one readPlayerLocale method");
        return writer.toByteArray();
    }

    public static void patch(Path input, Path output) throws Exception {
        if (input.toRealPath().equals(output.toAbsolutePath().normalize()) ||
                (Files.exists(output) && Files.isSameFile(input, output))) {
            throw new IllegalArgumentException("Use a separate output JAR");
        }
        String sum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)));
        if (!sum.equals(INPUT_SHA256)) throw new IllegalArgumentException("Unsupported original SimpleClans JAR: " + sum);
        var entries = new LinkedHashMap<String, byte[]>();
        try (var zip = new ZipFile(input.toFile())) {
            for (var it = zip.entries(); it.hasMoreElements();) {
                var entry = it.nextElement();
                if (!entry.isDirectory()) entries.put(entry.getName(), zip.getInputStream(entry).readAllBytes());
            }
        }
        String plugin = new String(entries.get("plugin.yml"), StandardCharsets.UTF_8);
        String old = "version: '2.19.2-05e7abc'";
        if (!plugin.contains(old) || entries.containsKey(HELPER + ".class")) throw new IllegalArgumentException("Unexpected plugin metadata");
        entries.put("plugin.yml", plugin.replace(old, "version: '" + VERSION + "'").getBytes(StandardCharsets.UTF_8));
        entries.put(MANAGER + ".class", patchManager(entries.get(MANAGER + ".class")));
        try (var helper = PatchSimpleClans.class.getResourceAsStream("/" + HELPER + ".class")) {
            if (helper == null) throw new IOException("ClientLocale helper missing from build");
            entries.put(HELPER + ".class", helper.readAllBytes());
        }
        for (String name : List.of("SimpleClans-GPL-3.0.txt", "ACF-MIT.txt", "NOTICE.txt")) {
            String path = "META-INF/server-fixes/" + name;
            try (var resource = PatchSimpleClans.class.getResourceAsStream("/" + path)) {
                if (resource == null) throw new IOException("Missing notice: " + path);
                entries.put(path, resource.readAllBytes());
            }
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(output))) {
            for (var entry : entries.entrySet()) {
                var metadata = new ZipEntry(entry.getKey());
                metadata.setTime(0);
                zip.putNextEntry(metadata); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: PatchSimpleClans original.jar fixed.jar");
        patch(Path.of(args[0]), Path.of(args[1]));
        System.out.println("Built SimpleClans " + VERSION + " -> " + args[1]);
    }
}
