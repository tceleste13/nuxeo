/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Nuxeo
 */

package org.nuxeo.ecm.blob.azure;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.blob.binary.FileStorage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;

/**
 * @author <a href="mailto:ak@nuxeo.com">Arnaud Kervern</a>
 * @since 7.10
 * @deprecated since 2023.9
 */
@Deprecated(since = "2023.9")
public class AzureFileStorage implements FileStorage {

    private static final Logger log = LogManager.getLogger(AzureFileStorage.class);

    protected BlobContainerClient container;

    protected String prefix;

    public AzureFileStorage(BlobContainerClient container, String prefix) {
        this.container = container;
        this.prefix = prefix;
    }

    @Override
    public void storeFile(String digest, File file) throws IOException {
        long t0 = System.currentTimeMillis();
        BlobClient blob = container.getBlobClient(prefix + digest);
        if (blob.exists()) {
            if (isBlobDigestCorrect(digest, blob)) {
                if (log.isDebugEnabled()) {
                    log.debug("blob " + digest + " is already in Azure");
                }
                return;
            }
        }

        try (InputStream is = new FileInputStream(file)) {
            blob.upload(is, file.length());
        }
        log.debug("stored blob: {} to Azure in {}ms", () -> digest, () -> System.currentTimeMillis() - t0);
    }

    @Override
    public boolean fetchFile(String digest, File file) throws IOException {
        log.debug("fetching blob: {} from Azure", digest);
        BlobClient blob = container.getBlobClient(prefix + digest);
        if (!blob.exists()) {
            return false;
        }
        if (!isBlobDigestCorrect(digest, blob)) {
            log.error("Invalid ETag in Azure, AzDigest: {} digest: {}",
                    () -> decodeContentMD5(blob.getProperties().getContentMd5()), () -> digest);
            return false;
        }
        try (OutputStream os = new FileOutputStream(file)) {
            blob.downloadStream(os);
        }
        return true;
    }

    @Override
    public boolean exists(String digest) {
        return container.getBlobClient(prefix + digest).exists();
    }

    protected static boolean isBlobDigestCorrect(String digest, BlobClient blob) {
        return isBlobDigestCorrect(digest, blob.getProperties().getContentMd5());
    }

    protected static boolean isBlobDigestCorrect(String digest, byte[] contentMD5) {
        return digest.equals(decodeContentMD5(contentMD5));
    }

    protected static String decodeContentMD5(byte[] bytes) {
        try {
            return Hex.encodeHexString(bytes);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
