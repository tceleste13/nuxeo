/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.nuxeo.ecm.core.utils;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

/**
 * @since 2023.7
 */
@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.ecm.core.api.tests:OSGI-INF/test-zipfile-reader-charset-fallback-contrib.xml")
public class TestZipFileReader {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testReadCustomEncodedZipFile() throws IOException {
        File zipFile = tmp.newFile();
        String accentuatedFileName = "è_é";

        // Write a custom encoded ZipFile
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile), Charset.forName("cp850"))) {
            ZipEntry entry = new ZipEntry(accentuatedFileName);
            zos.putNextEntry(entry);
            zos.closeEntry();
            zos.finish();
        }

        // Read it
        try (var zfr = ZipFileReader.newZipFile(zipFile)) {
            assertNotNull(zfr.getEntry(accentuatedFileName));
        }
    }

}
