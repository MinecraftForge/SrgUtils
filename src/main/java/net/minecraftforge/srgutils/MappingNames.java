/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.srgutils;

import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.Map;

class MappingNames implements IMappingNames {
    private final @Unmodifiable Map<String, String> names;
    private final @Unmodifiable Map<String, String> docs;

    MappingNames(Map<String, String> names, Map<String, String> docs) {
        this.names = Collections.unmodifiableMap(names);
        this.docs = Collections.unmodifiableMap(docs);
    }

    @Override
    public @Unmodifiable Map<String, String> getNames() {
        return this.names;
    }

    @Override
    public @Unmodifiable Map<String, String> getDocs() {
        return this.docs;
    }

    public IRenamer renamer() {
        return new IRenamer() {
            @Override
            public String rename(IMappingFile.IPackage value) {
                return names.getOrDefault(value.getMapped(), value.getMapped());
            }

            @Override
            public String rename(IMappingFile.IClass value) {
                return names.getOrDefault(value.getMapped(), value.getMapped());
            }

            @Override
            public String rename(IMappingFile.IField value) {
                return names.getOrDefault(value.getMapped(), value.getMapped());
            }

            @Override
            public String rename(IMappingFile.IMethod value) {
                return names.getOrDefault(value.getMapped(), value.getMapped());
            }

            @Override
            public String rename(IMappingFile.IParameter value) {
                return names.getOrDefault(value.getMapped(), value.getMapped());
            }
        };
    }
}
