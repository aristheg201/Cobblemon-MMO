package vn.svframe.svframemmo.cobblemon.cosmetic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Validates configured Snowstorm descriptors before they are contributed to Polymer's server pack. */
public final class SnowstormAssetLoader {
    public Bundle load(Path root) throws java.io.IOException {
        Path particles = root.resolve("particles");
        Path textures = root.resolve("textures");
        if (!Files.isDirectory(particles)) return new Bundle(Map.of(), Set.of());
        LinkedHashMap<String, byte[]> assets = new LinkedHashMap<>();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        LinkedHashSet<String> requiredTextures = new LinkedHashSet<>();
        long total = 0L;
        try (var stream = Files.walk(particles)) {
            for (Path file : stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".particle.json")).sorted().toList()) {
                byte[] bytes = Files.readAllBytes(file);
                if (bytes.length < 1 || bytes.length > 512 * 1024) throw new java.io.IOException("Invalid Snowstorm descriptor size: " + file);
                total += bytes.length;
                JsonObject rootJson = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject effect = rootJson.getAsJsonObject("particle_effect");
                JsonObject description = effect == null ? null : effect.getAsJsonObject("description");
                if (description == null) throw new java.io.IOException("Missing particle_effect.description: " + file);
                String id = description.get("identifier").getAsString();
                if (!safeId(id)) throw new java.io.IOException("Unsafe Snowstorm id " + id);
                if (!ids.add(id)) throw new java.io.IOException("Duplicate Snowstorm id " + id);
                JsonObject render = description.getAsJsonObject("basic_render_parameters");
                String texture = render.get("texture").getAsString();
                if (!safeId(texture)) throw new java.io.IOException("Unsafe Snowstorm texture " + texture);
                requiredTextures.add(texture);
                String namespace = id.substring(0, id.indexOf(':'));
                String relative = particles.relativize(file).toString().replace('\\', '/');
                assets.put("assets/" + namespace + "/bedrock/particles/" + relative, bytes);
            }
        }
        for (String texture : requiredTextures) {
            int colon = texture.indexOf(':');
            String namespace = texture.substring(0, colon);
            String path = texture.substring(colon + 1);
            Path file = textures.resolve(namespace).resolve(path + ".png").normalize();
            if (!file.startsWith(textures.normalize()) || !Files.isRegularFile(file)) throw new java.io.IOException("Missing Snowstorm texture " + texture);
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length < 1 || bytes.length > 1024 * 1024) throw new java.io.IOException("Invalid Snowstorm texture size " + file);
            var image = ImageIO.read(file.toFile());
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1 || image.getWidth() > 256 || image.getHeight() > 256)
                throw new java.io.IOException("Invalid Snowstorm texture dimensions " + file);
            total += bytes.length;
            assets.put("assets/" + namespace + "/textures/" + path + ".png", bytes);
        }
        if (total > 32L * 1024L * 1024L) throw new java.io.IOException("Snowstorm assets exceed 32 MiB");
        return new Bundle(Map.copyOf(assets), Set.copyOf(ids));
    }

    private static boolean safeId(String value) {
        return value != null && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") && !value.contains("..") && !value.startsWith("/") && !value.endsWith("/");
    }

    public record Bundle(Map<String, byte[]> assets, Set<String> particleIds) { }
}
