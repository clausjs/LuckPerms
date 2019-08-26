/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.extension;

import com.google.gson.JsonElement;

import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.util.gson.GsonProvider;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.extension.Extension;
import net.luckperms.api.extension.ExtensionManager;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SimpleExtensionManager implements ExtensionManager, AutoCloseable {
    private final LuckPermsPlugin plugin;
    private final Set<LoadedExtension> extensions = new HashSet<>();

    public SimpleExtensionManager(LuckPermsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void close() {
        for (LoadedExtension extension : this.extensions) {
            try {
                extension.instance.unload();
                if (extension.classLoader != null) {
                    extension.classLoader.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void loadExtension(Extension extension) {
        if (this.extensions.stream().anyMatch(e -> e.instance.equals(extension))) {
            return;
        }
        this.extensions.add(new LoadedExtension(extension, null, null));
        extension.load();
        this.plugin.getEventFactory().handleExtensionLoad(extension);
    }

    public void loadExtensions(Path directory) {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return;
        }

        try (Stream<Path> stream = Files.list(directory)) {
            stream.forEach(path -> {
                try {
                    loadExtension(path);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public @NonNull Extension loadExtension(Path path) throws IOException {
        if (this.extensions.stream().anyMatch(e -> path.equals(e.path))) {
            throw new IllegalStateException("Extension at path " + path.toString() + " already loaded.");
        }

        if (!Files.exists(path)) {
            throw new NoSuchFileException("No file at " + path);
        }

        URLClassLoader classLoader = new URLClassLoader(new URL[]{path.toUri().toURL()}, getClass().getClassLoader());
        String className;

        try (InputStream in = classLoader.getResourceAsStream("extension.json")) {
            if (in == null) {
                throw new RuntimeException("extension.json not present in " + path.toString());
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                JsonElement parsed = GsonProvider.parser().parse(reader);
                className = parsed.getAsJsonObject().get("class").getAsString();
            }
        }

        if (className == null) {
            throw new IllegalArgumentException("class is null");
        }

        Class<? extends Extension> extensionClass;
        try {
            extensionClass = classLoader.loadClass(className).asSubclass(Extension.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        Extension extension = null;

        try {
            Constructor<? extends Extension> constructor = extensionClass.getConstructor(LuckPerms.class);
            extension = constructor.newInstance(this.plugin.getApiProvider());
        } catch (NoSuchMethodException e) {
            // ignore
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        if (extension == null) {
            try {
                Constructor<? extends Extension> constructor = extensionClass.getConstructor();
                extension = constructor.newInstance();
            } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        this.extensions.add(new LoadedExtension(extension, classLoader, path));
        extension.load();
        this.plugin.getEventFactory().handleExtensionLoad(extension);
        return extension;
    }

    @Override
    public @NonNull Collection<Extension> getLoadedExtensions() {
        return this.extensions.stream().map(e -> e.instance).collect(Collectors.toSet());
    }

    private static final class LoadedExtension {
        private final Extension instance;
        private final URLClassLoader classLoader;
        private final Path path;

        private LoadedExtension(Extension instance, URLClassLoader classLoader, Path path) {
            this.instance = instance;
            this.classLoader = classLoader;
            this.path = path;
        }
    }
}
