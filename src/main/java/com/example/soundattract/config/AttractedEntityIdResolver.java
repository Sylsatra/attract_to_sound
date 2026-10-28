package com.example.soundattract.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AttractedEntityIdResolver {
    private AttractedEntityIdResolver() {}

    public static Set<String> resolveAttractedEntityIds(List<String> attractedEntries, List<String> blacklistEntries, Set<String> registeredEntityIds) {
        Set<String> result = new HashSet<>();
        for (String entry : attractedEntries) {
            if (isNamespaceWildcard(entry)) {
                String namespace = entry.substring(0, entry.length() - 2);
                if (isValidNamespace(namespace)) {
                    for (String registeredId : registeredEntityIds) {
                        if (registeredId != null && registeredId.startsWith(namespace + ":")) {
                            result.add(registeredId);
                        }
                    }
                }
                continue;
            }

            if (isValidEntityId(entry) && registeredEntityIds.contains(entry)) {
                result.add(entry);
            }
        }

        for (String entry : blacklistEntries) {
            if (isValidEntityId(entry)) {
                result.remove(entry);
            }
        }
        return result;
    }

    public static boolean isNamespaceWildcard(String entry) {
        return entry != null && entry.endsWith(":*") && entry.length() > 2;
    }

    public static boolean isValidNamespace(String namespace) {
        return namespace != null && namespace.matches("[a-z0-9_.-]+");
    }

    private static boolean isValidEntityId(String entry) {
        if (entry == null) {
            return false;
        }
        int separator = entry.indexOf(':');
        if (separator <= 0 || separator != entry.lastIndexOf(':') || separator == entry.length() - 1) {
            return false;
        }
        return isValidNamespace(entry.substring(0, separator)) && entry.substring(separator + 1).matches("[a-z0-9_/.-]+");
    }
}