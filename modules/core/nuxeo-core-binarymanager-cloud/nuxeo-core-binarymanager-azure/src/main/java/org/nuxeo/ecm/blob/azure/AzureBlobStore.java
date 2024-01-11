/*
 * (C) Copyright 2023 Nuxeo (http://nuxeo.com/) and others.
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
 *     Guillaume Renard
 */
package org.nuxeo.ecm.blob.azure;

import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;
import static org.nuxeo.ecm.core.blob.BlobProviderDescriptor.ALLOW_BYTE_RANGE;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;

import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.AbstractBlobGarbageCollector;
import org.nuxeo.ecm.core.blob.AbstractBlobStore;
import org.nuxeo.ecm.core.blob.BlobContext;
import org.nuxeo.ecm.core.blob.BlobStore;
import org.nuxeo.ecm.core.blob.BlobWriteContext;
import org.nuxeo.ecm.core.blob.ByteRange;
import org.nuxeo.ecm.core.blob.KeyStrategy;
import org.nuxeo.ecm.core.blob.KeyStrategyDigest;
import org.nuxeo.ecm.core.blob.binary.BinaryGarbageCollector;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.ResultContinuation;
import com.microsoft.azure.storage.ResultSegment;
import com.microsoft.azure.storage.StorageErrorCode;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobListingDetails;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;

/**
 * @since 2023.6
 */
public class AzureBlobStore extends AbstractBlobStore {

    protected static final Logger log = LogManager.getLogger(AzureBlobStore.class);

    protected final AzureBlobStoreConfiguration config;

    protected final BinaryGarbageCollector gc;

    protected CloudStorageAccount storageAccount;

    protected CloudBlobClient blobClient;

    protected CloudBlobContainer container;

    protected String prefix;

    protected boolean allowByteRange;

    public AzureBlobStore(String blobProviderId, String name, AzureBlobStoreConfiguration config,
            KeyStrategy keyStrategy) {
        super(blobProviderId, name, keyStrategy);
        this.config = config;
        this.storageAccount = config.storageAccount;
        this.blobClient = config.blobClient;
        this.container = config.container;
        this.prefix = config.prefix;
        this.allowByteRange = config.getBooleanProperty(ALLOW_BYTE_RANGE);
        this.gc = new AzureBlobGarbageCollector();
    }

    public class AzureBlobGarbageCollector extends AbstractBlobGarbageCollector {

        @Override
        public void computeToDelete() {
            toDelete = new HashSet<>();
            ResultContinuation continuationToken = null;
            ResultSegment<ListBlobItem> lbs;
            do {
                try {
                    lbs = container.listBlobsSegmented(prefix, false, EnumSet.noneOf(BlobListingDetails.class), null,
                            continuationToken, null, null);
                } catch (StorageException e) {
                    throw new NuxeoException(e);
                }
                // ignore subdirectories by considering only instances of CloudBlockBlob
                lbs.getResults().stream().filter(CloudBlockBlob.class::isInstance).forEach(item -> {
                    CloudBlockBlob blob = (CloudBlockBlob) item;

                    String name = blob.getName();
                    String digest = name.substring(prefix.length());

                    if (!((KeyStrategyDigest) keyStrategy).isValidDigest(digest)) {
                        // ignore blobs that cannot be digests, for safety
                        return;
                    }

                    long length = blob.getProperties().getLength();
                    status.sizeBinaries += length;
                    status.numBinaries++;
                    toDelete.add(digest);
                });
                continuationToken = lbs.getContinuationToken();
            } while (lbs.getHasMoreResults());
        }

        @Override
        public String getId() {
            return "azure:" + container + "/" + prefix;
        }

        @Override
        public void mark(String key) {
            toDelete.remove(key);
        }

        @Override
        public void removeUnmarkedBlobsAndUpdateStatus(boolean delete) {
            for (String digest : toDelete) {
                try {
                    CloudBlockBlob blob = container.getBlockBlobReference(prefix + digest);
                    if (!blob.exists()) {
                        // shouldn't happen except if blob concurrently removed
                        continue;
                    }
                    blob.downloadAttributes();
                    long length = blob.getProperties().getLength();
                    status.sizeBinariesGC += length;
                    status.numBinariesGC++;
                    status.sizeBinaries -= length;
                    status.numBinaries--;
                    if (delete) {
                        deleteBlob(digest);
                    }
                } catch (URISyntaxException | StorageException e) {
                    throw new NuxeoException(e);
                }
            }
        }
    }

    @Override
    public boolean copyBlobIsOptimized(BlobStore sourceStore) {
        return false;
    }

    @Override
    public String copyOrMoveBlob(String key, BlobStore sourceStore, String sourceKey, boolean move) throws IOException {
        return copyOrMoveBlobGeneric(key, sourceStore, sourceKey, move);
    }

    protected String copyOrMoveBlobGeneric(String key, BlobStore sourceStore, String sourceKey, boolean atomicMove)
            throws IOException {
        Path tmp = null;
        try {
            OptionalOrUnknown<Path> fileOpt = sourceStore.getFile(sourceKey);
            Path file;
            if (fileOpt.isPresent()) {
                file = fileOpt.get();
            } else {
                // no local file available, read from source
                tmp = Files.createTempFile("bin_", ".tmp");
                boolean found = sourceStore.readBlob(sourceKey, tmp);
                if (!found) {
                    return null;
                }
                file = tmp;
            }
            CloudBlockBlob blob;
            try {
                blob = container.getBlockBlobReference(prefix + key);
                // if the digest is not already known then save to Azure
                if (!blob.exists()) {
                    File pathFile = file.toFile();
                    try (InputStream is = new FileInputStream(pathFile)) {
                        blob.upload(is, pathFile.length());
                    }
                }
            } catch (StorageException | URISyntaxException e) {
                throw new IOException(e);
            }
            if (atomicMove) {
                sourceStore.deleteBlob(sourceKey);
            }
            return key;
        } finally {
            if (tmp != null) {
                try {
                    Files.delete(tmp);
                } catch (IOException e) {
                    log.warn(e, e);
                }
            }
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            CloudBlockBlob blob = container.getBlockBlobReference(prefix + key);
            return blob.exists();
        } catch (StorageException e) {
            if (!e.getErrorCode().equals(StorageErrorCode.RESOURCE_NOT_FOUND.toString())) {
                log.error("Error while checking if key: {} exists", key, e);
            }
        } catch (URISyntaxException e) {
            log.error("Error while checking if key: {} exists", key, e);
        }
        return false;
    }

    @Override
    public boolean hasVersioning() {
        // Maybe later
        return false;
    }

    @Override
    public OptionalOrUnknown<Path> getFile(String key) {
        return OptionalOrUnknown.unknown();
    }

    @Override
    public OptionalOrUnknown<InputStream> getStream(String key) throws IOException {
        try {
            CloudBlockBlob blob = container.getBlockBlobReference(prefix + key);
            if (!blob.exists()) {
                return OptionalOrUnknown.missing();
            }
            return OptionalOrUnknown.of(blob.openInputStream());
        } catch (StorageException | URISyntaxException e) {
            log.error("Error while getting stream for key: {}", key, e);
            return OptionalOrUnknown.missing();
        }
    }

    @Override
    public boolean readBlob(String key, Path dest) throws IOException {
        ByteRange byteRange;
        if (allowByteRange) {
            MutableObject<String> keyHolder = new MutableObject<>(key);
            byteRange = getByteRangeFromKey(keyHolder);
            key = keyHolder.getValue();
        } else {
            byteRange = null;
        }
        log.debug("fetching blob: {} from Azure", key);
        try {
            CloudBlockBlob blob = container.getBlockBlobReference(prefix + key);
            if (!blob.exists()) {
                return false;
            }
            try (OutputStream os = new FileOutputStream(dest.toFile())) {
                if (byteRange != null) {
                    blob.downloadRange(byteRange.getStart(), byteRange.getLength(), os);
                } else {
                    blob.download(os);
                }
            }
            return true;
        } catch (URISyntaxException e) {
            throw new IOException(e);
        } catch (StorageException e) {
            return false;
        }
    }

    @Override
    protected String writeBlobGeneric(BlobWriteContext blobWriteContext) throws IOException {
        Path file;
        Path tmp = null;
        try {
            BlobContext blobContext = blobWriteContext.blobContext;
            Path blobWriteContextFile = blobWriteContext.getFile();
            if (blobWriteContextFile != null) { // we have a file, assume that the caller already observed the write
                file = blobWriteContextFile;
            } else {
                // no transfer to a file was done yet (no caching)
                // we may be able to use the blob's underlying file, if not pure streaming
                File blobFile = blobContext.blob.getFile();
                if (blobFile != null) { // otherwise use blob file directly
                    if (blobWriteContext.writeObserver != null) { // but we must still run the writes through the write
                                                                  // observer
                        transfer(blobWriteContext, NULL_OUTPUT_STREAM);
                    }
                    file = blobFile.toPath();
                } else {
                    // we must transfer the blob stream to a tmp file
                    tmp = Files.createTempFile("bin_", ".tmp");
                    transfer(blobWriteContext, tmp);
                    file = tmp;
                }
            }
            String key = blobWriteContext.getKey(); // may depend on write observer, for example for digests
            if (key == null) {
                // should never happen unless an invalid WriteObserver is used in new code
                throw new NuxeoException("Missing key");
            }
            // if the digest is not already known then save to Azure
            log.debug("Storing blob with digest: {} to Azure", key);
            CloudBlockBlob blob;
            try {
                blob = container.getBlockBlobReference(prefix + key);
                if (!blob.exists()) {
                    File pathFile = file.toFile();
                    try (InputStream is = new FileInputStream(pathFile)) {
                        blob.upload(is, pathFile.length());
                    }
                }
            } catch (StorageException | URISyntaxException e) {
                throw new IOException(e);
            }
            return key;
        } finally {
            if (tmp != null) {
                try {
                    Files.delete(tmp);
                } catch (IOException e) {
                    log.warn(e, e);
                }
            }
        }

    }

    @Override
    public void deleteBlob(String key) {
        try {
            CloudBlockBlob blob = container.getBlockBlobReference(prefix + key);
            if (blob.exists()) {
                blob.delete();
            }
        } catch (StorageException | URISyntaxException e) {
            log.warn("Unable to remove blob: {}", key, e);
        }
    }

    @Override
    public BinaryGarbageCollector getBinaryGarbageCollector() {
        return gc;
    }

    @Override
    public void clear() {
        ResultContinuation continuationToken = null;
        ResultSegment<ListBlobItem> lbs;
        do {
            try {
                lbs = container.listBlobsSegmented(prefix, false, EnumSet.noneOf(BlobListingDetails.class), null,
                        continuationToken, null, null);
            } catch (StorageException e) {
                throw new NuxeoException(e);
            }

            // ignore subdirectories by considering only instances of CloudBlockBlob
            lbs.getResults()
               .stream()
               .filter(CloudBlockBlob.class::isInstance)
               .map(item -> ((CloudBlockBlob) item).getName().substring(prefix.length()))
               .forEach((key -> deleteBlob(key)));
            continuationToken = lbs.getContinuationToken();
        } while (lbs.getHasMoreResults());
    }

}
