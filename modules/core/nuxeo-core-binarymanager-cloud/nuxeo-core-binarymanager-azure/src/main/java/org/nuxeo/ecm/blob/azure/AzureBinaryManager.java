/*
 * (C) Copyright 2015-2018 Nuxeo (http://nuxeo.com/) and others.
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

import static org.nuxeo.ecm.blob.azure.AzureBlobStoreConfiguration.AZURE_STORAGE_ACCESS_KEY_ENV_VAR;
import static org.nuxeo.ecm.blob.azure.AzureBlobStoreConfiguration.AZURE_STORAGE_ACCOUNT_ENV_VAR;
import static org.nuxeo.ecm.blob.azure.AzureBlobStoreConfiguration.DELIMITER;

import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.blob.AbstractCloudBinaryManager;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.blob.binary.BinaryGarbageCollector;
import org.nuxeo.ecm.core.blob.binary.FileStorage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.StorageSharedKeyCredential;

/**
 * @author <a href="mailto:ak@nuxeo.com">Arnaud Kervern</a>
 * @since 7.10
 */
public class AzureBinaryManager extends AbstractCloudBinaryManager {

    public static final String ENDPOINT_PROTOCOL_PROPERTY = "endpointProtocol";

    public static final String SYSTEM_PROPERTY_PREFIX = "nuxeo.storage.azure";

    public static final String ACCOUNT_NAME_PROPERTY = AzureBlobStoreConfiguration.ACCOUNT_NAME_PROPERTY;

    public static final String ACCOUNT_KEY_PROPERTY = AzureBlobStoreConfiguration.ACCOUNT_KEY_PROPERTY;

    public static final String CONTAINER_PROPERTY = "container";

    /** @since 10.10 */
    public static final String PREFIX_PROPERTY = "prefix";

    protected BlobContainerClient client;

    protected String prefix;

    @Override
    protected String getSystemPropertyPrefix() {
        return SYSTEM_PROPERTY_PREFIX;
    }

    @Override
    protected void setupCloudClient() throws IOException {
        if (StringUtils.isBlank(properties.get(ACCOUNT_KEY_PROPERTY))) {
            properties.put(ACCOUNT_NAME_PROPERTY, System.getenv(AZURE_STORAGE_ACCOUNT_ENV_VAR));
            properties.put(ACCOUNT_KEY_PROPERTY, System.getenv(AZURE_STORAGE_ACCESS_KEY_ENV_VAR));
        }
        String containerName = getProperty(CONTAINER_PROPERTY);
        String accountName = getProperty(ACCOUNT_NAME_PROPERTY);
        // accountName and containerName are conf properties, not user inputs, no need to sanitize
        String endpoint = String.format("https://%s.blob.core.windows.net/%s", accountName, containerName);
        client = new BlobContainerClientBuilder().endpoint(endpoint)
                                                 .credential(new StorageSharedKeyCredential(accountName,
                                                         getProperty(ACCOUNT_KEY_PROPERTY)))
                                                 .buildClient();
        client.createIfNotExists();
        prefix = StringUtils.defaultIfBlank(properties.get(PREFIX_PROPERTY), "");
        if (StringUtils.isNotBlank(prefix) && !prefix.endsWith(DELIMITER)) {
            prefix += DELIMITER;
        }
        if (StringUtils.isNotBlank(namespace)) {
            // use namespace as an additional prefix
            prefix += namespace;
            if (!prefix.endsWith(DELIMITER)) {
                prefix += DELIMITER;
            }
        }
    }

    @Override
    protected BinaryGarbageCollector instantiateGarbageCollector() {
        return new AzureGarbageCollector(this);
    }

    @Override
    protected FileStorage getFileStorage() {
        return new AzureFileStorage(client, prefix);
    }

    @Override
    protected URI getRemoteUri(String digest, ManagedBlob blob, HttpServletRequest servletRequest) throws IOException {
        BlobClient blobClient = client.getBlobClient(digest);

        BlobSasPermission permissions = new BlobSasPermission().setReadPermission(true);

        OffsetDateTime expiryTime = OffsetDateTime.now().plusSeconds(directDownloadExpire);

        // build the token
        BlobServiceSasSignatureValues sasSignatureValues = new BlobServiceSasSignatureValues(expiryTime, permissions);
        sasSignatureValues.setContentDisposition(getContentDispositionHeader(blob, servletRequest));
        sasSignatureValues.setContentType(getContentTypeHeader(blob));
        String sas = blobClient.generateSas(sasSignatureValues);

        return URI.create(blobClient.getBlobUrl() + "?" + sas);
    }

    @Override
    protected String getContentDispositionHeader(Blob blob, HttpServletRequest servletRequest) {
        // Azure will do the %-encoding itself, pass it a String directly
        return "attachment; filename*=UTF-8''" + blob.getFilename();
    }

    protected void removeBinary(String digest) {
        client.getBlobClient(prefix + digest).deleteIfExists();
    }

    @Override
    public void removeBinaries(Collection<String> digests) {
        digests.forEach(this::removeBinary);
    }

    /**
     * @since 11.5
     * @return the length of the blob with the given {@code digest}, or -1 if missing
     */
    protected long lengthOfBlob(String digest) {
        BlobClient blobClient = client.getBlobClient(prefix + digest);
        if (blobClient != null) {
            return blobClient.getProperties().getBlobSize();
        }
        return -1;
    }

}
