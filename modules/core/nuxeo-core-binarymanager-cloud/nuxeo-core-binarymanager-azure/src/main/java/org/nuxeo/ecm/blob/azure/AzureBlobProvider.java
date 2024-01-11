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

import static org.nuxeo.common.utils.RFC2231.encodeContentDisposition;
import static org.nuxeo.ecm.blob.azure.AzureBlobStoreConfiguration.SYSTEM_PROPERTY_PREFIX;
import static org.nuxeo.ecm.core.io.download.DownloadHelper.getContentTypeHeader;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Date;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobStore;
import org.nuxeo.ecm.core.blob.BlobStoreBlobProvider;
import org.nuxeo.ecm.core.blob.CachingBlobStore;
import org.nuxeo.ecm.core.blob.CachingConfiguration;
import org.nuxeo.ecm.core.blob.DigestConfiguration;
import org.nuxeo.ecm.core.blob.KeyStrategy;
import org.nuxeo.ecm.core.blob.KeyStrategyDigest;
import org.nuxeo.ecm.core.blob.ManagedBlob;

import com.microsoft.azure.storage.StorageCredentialsSharedAccessSignature;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.SharedAccessBlobHeaders;
import com.microsoft.azure.storage.blob.SharedAccessBlobPolicy;

/**
 * Blob provider that stores files in Azure Storage.
 * <p>
 * This implementation only supports {@link KeyStrategyDigest} which is the legacy strategy.
 * <p>
 * This implementation does not support transactional mode.
 *
 * @since 2023.6
 */
public class AzureBlobProvider extends BlobStoreBlobProvider {

    public static final String STORE_SCROLL_NAME = "azureBlobScroll";

    protected DigestConfiguration digestConfiguration;

    protected AzureBlobStoreConfiguration config;

    @Override
    public void close() {
        // Do nothing
    }

    @Override
    protected BlobStore getBlobStore(String blobProviderId, Map<String, String> properties) throws IOException {
        config = new AzureBlobStoreConfiguration(properties);
        digestConfiguration = new DigestConfiguration(SYSTEM_PROPERTY_PREFIX, properties);
        KeyStrategy keyStrategy = getKeyStrategy();
        if (!(keyStrategy instanceof KeyStrategyDigest)) {
            throw new UnsupportedOperationException("Azure Blob Provider only supports KeyStrategyDigest");
        }
        KeyStrategyDigest ksd = (KeyStrategyDigest) keyStrategy;
        BlobStore store = new AzureBlobStore(blobProviderId, "azureStorage", config, ksd);
        boolean caching = !config.getBooleanProperty("nocache");
        if (caching) {
            CachingConfiguration cachingConfiguration = new CachingConfiguration(SYSTEM_PROPERTY_PREFIX, properties);
            store = new CachingBlobStore(blobProviderId, "Cache", store, cachingConfiguration);
        }
        return store;
    }

    @Override
    protected String getDigestAlgorithm() {
        return digestConfiguration.digestAlgorithm;
    }

    @Override
    public String getStoreScrollName() {
        return STORE_SCROLL_NAME;
    }

    @Override
    public URI getURI(ManagedBlob blob, BlobManager.UsageHint hint, HttpServletRequest servletRequest)
            throws IOException {
        if (hint != BlobManager.UsageHint.DOWNLOAD || !config.directDownload) {
            return null;
        }
        String bucketKey = config.prefix + stripBlobKeyPrefix(blob.getKey());
        Date expiration = new Date(System.currentTimeMillis() + config.directDownloadExpire * 1000);
        if (StringUtils.isNotBlank(config.cdnHost)) {
            return getURICDN(bucketKey, blob, expiration);
        } else {
            return getURIAzure(bucketKey, blob, expiration);
        }
    }

    /**
     * Gets a URI for the given blob for direct download via CDN.
     */
    protected URI getURICDN(String key, ManagedBlob blob, Date expiration) throws IOException {
        URI azure = getURIAzure(key, blob, expiration);
        String cdn = azure.toString().replace(azure.getHost(), config.cdnHost);
        return URI.create(cdn);
    }

    /**
     * Gets a URI for the given blob for direct download.
     */
    protected URI getURIAzure(String key, ManagedBlob blob, Date expiration) throws IOException {
        try {
            CloudBlockBlob blockBlobReference = config.container.getBlockBlobReference(key);
            SharedAccessBlobPolicy policy = new SharedAccessBlobPolicy();
            policy.setPermissionsFromString("r");

            policy.setSharedAccessExpiryTime(expiration);

            SharedAccessBlobHeaders headers = new SharedAccessBlobHeaders();
            headers.setContentDisposition(encodeContentDisposition(blob.getFilename(), false, null));
            headers.setContentType(getContentTypeHeader(blob));

            String sas = blockBlobReference.generateSharedAccessSignature(policy, headers, null);

            CloudBlockBlob signedBlob = new CloudBlockBlob(blockBlobReference.getUri(),
                    new StorageCredentialsSharedAccessSignature(sas));
            return signedBlob.getSnapshotQualifiedUri();
        } catch (URISyntaxException | InvalidKeyException | StorageException e) {
            throw new IOException(e);
        }
    }

}
