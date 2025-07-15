/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.srgutils;

import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public interface IMappingNames {
    @Unmodifiable Map<String, String> getNames();

    @Unmodifiable Map<String, String> getDocs();

    IRenamer renamer();

    static IMappingNames load(File data) throws IOException {
        return InternalUtils.loadNamesCsv(data);
    }
}
