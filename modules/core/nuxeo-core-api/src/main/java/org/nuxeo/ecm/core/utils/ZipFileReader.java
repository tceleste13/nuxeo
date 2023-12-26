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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.services.config.ConfigurationService;

/**
 * Utility class to read {@link ZipFile}s.
 *
 * @since 2023.7
 */
public class ZipFileReader {

    private static final Logger log = LogManager.getLogger(ZipFileReader.class);

    public static final String NUXEO_ZIP_FILE_READER_CHARSET_FALLBACK_KEY = "org.nuxeo.ecm.zip.file.reader.charset.fallback";

    private ZipFileReader() {
    }

    /**
     * Reads a {@link ZipFile} as UTF-8 encoded. Falls back on a configurable {@link Charset} otherwise.
     *
     * @return a {@link ZipFile} decoded into UTF-8 if possible, or into a configured {@link Charset} if possible
     * @throws IOException if decoding goes wrong
     */
    public static ZipFile newZipFile(File file) throws IOException {
        try {
            return new ZipFile(file);
        } catch (ZipException ze) {
            log.debug("Failed decoding ZipFile into {}. Checking for a fallback Charset.", UTF_8);
            Optional<String> charsetName = Framework.getService(ConfigurationService.class)
                                                    .getString(NUXEO_ZIP_FILE_READER_CHARSET_FALLBACK_KEY);
            Optional<Charset> charset = charsetName.map(Charset::forName);
            if (charset.isPresent()) {
                log.debug("Trying to decode ZipFile into {} as fallback charset.", charset.get());
                return new ZipFile(file, charset.get());
            }
            log.warn("Failed decoding ZipFile: {}", file);
            throw ze;
        }
    }

    public static ZipFile newZipFile(String source) throws IOException {
        return newZipFile(new File(source));
    }

}
