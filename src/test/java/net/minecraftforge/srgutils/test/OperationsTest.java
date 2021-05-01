/*
 * SRG Utils
 * Copyright (c) 2021
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.srgutils.test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.srgutils.CompleteRenamer;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IMappingFile.Format;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OperationsTest
{

    Path root = getRoot().resolve("Operations/");
    IMappingFile srgA, srgB;
    static FileSystem imfs = Jimfs.newFileSystem(Configuration.unix());

    private Path getRoot() {
        URL url = this.getClass().getResource("/test.marker");
        Assertions.assertNotNull(url, "Could not find test.marker");
        try {
            return new File(url.toURI()).getParentFile().toPath();
        } catch (URISyntaxException e) {
            return new File(url.getPath()).getParentFile().toPath();
        }
    }

    @BeforeEach
    public void init() throws IOException {
        this.srgA = IMappingFile.load(Files.newInputStream(root.resolve("input/A.txt")));
        this.srgB = IMappingFile.load(Files.newInputStream(root.resolve("input/B.txt")));
    }

    @Test
    public void testRename() throws IOException {
        test(srgA.rename(new CompleteRenamer(srgB)), imfs.getPath("./out.txt"), Format.TSRG2, root.resolve("pattern/rename.txt"));
    }

    @Test
    public void testFill() throws IOException {
        test(srgA.fill(srgB), imfs.getPath("./out.txt"), Format.TSRG2, root.resolve("pattern/fill.txt"));
    }

    @AfterAll
    public static void exit() throws IOException {
        imfs.close();
    }

    private void test(IMappingFile result, Path dest, Format format, Path pattern) throws IOException {
        result.write(dest, format, false);
        Assertions.assertEquals(getFileContents(pattern), getFileContents(dest), "Pattern differ: " + pattern.getFileName());
    }

    private String getFileContents(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + file.toAbsolutePath(), e);
        }
    }
}
