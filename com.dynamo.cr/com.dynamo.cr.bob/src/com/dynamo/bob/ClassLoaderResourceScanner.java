package com.dynamo.bob;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FilenameUtils;

public class ClassLoaderResourceScanner implements IResourceScanner {

    private static void scanDir(File dir, String filter, Set<String> results) {
        File[] files = dir.listFiles();
        for (File file : files) {
            if (FilenameUtils.wildcardMatch(file.getName(), filter)) {
                results.add(file.getName());
            }
        }
    }

    private static void scanJar(URL resource, String filter, Set<String> results) throws IOException {
        String resPath = resource.getPath();
        String jarPath = resPath.replaceFirst("[.]jar[!].*", ".jar").replaceFirst("file:", "");
        JarFile jarFile;
        jarFile = new JarFile(jarPath);
        Enumeration<JarEntry> entries = jarFile.entries();
        while(entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if(!entry.isDirectory() && FilenameUtils.wildcardMatch(entryName, filter)) {
                results.add(entryName);
            }
        }
    }

    @Override
    public Set<String> scan(String filter) {
        Set<String> results = new HashSet<String>();
        ClassLoader classLoader = this.getClass().getClassLoader();

        String baseDir = filter;
        baseDir = baseDir.substring(0, Math.min(baseDir.lastIndexOf('/'), baseDir.lastIndexOf('*')));
        try {
            Enumeration<URL> e = classLoader.getResources(baseDir);
            while (e.hasMoreElements()) {
                URL url = e.nextElement();
                String proto = url.getProtocol();
                if (proto.equals("file")) {
                    File dir = new File(url.getFile());
                    scanDir(dir, filter, results);
                } else if (proto.equals("jar")) {
                    scanJar(url, filter, results);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return results;
    }

}
