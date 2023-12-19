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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.scroll.AbstractBlobScroll;

import com.microsoft.azure.storage.ResultContinuation;
import com.microsoft.azure.storage.ResultSegment;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.BlobListingDetails;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;

/**
 * Scroll blobs of the Azure blob store of a {@link AzureBlobProvider}, the scroll query is the
 * provider id.
 *
 * @since 2023.6
 */
public class AzureBlobScroll extends AbstractBlobScroll<AzureBlobProvider> {

    protected AzureBlobStore store;

    protected int prefixLength;

    protected ResultSegment<ListBlobItem> lbs;

    protected ResultContinuation continuationToken = null;

    @Override
    protected void init(AzureBlobProvider provider) {
        this.store = (AzureBlobStore) provider.store.unwrap();
        this.prefixLength = this.store.prefix.length();
    }

    @Override
    public boolean hasNext() {
        return lbs == null || lbs.getHasMoreResults();
    }

    @Override
    public List<String> next() {
        if (lbs != null && !lbs.getHasMoreResults()) {
            throw new NoSuchElementException();
        }
        try {
            lbs = store.container.listBlobsSegmented(store.prefix, false, EnumSet.noneOf(BlobListingDetails.class),
                    size, continuationToken, null, null);
        } catch (StorageException e) {
            throw new NuxeoException(e);
        }
        continuationToken = lbs.getContinuationToken();
        List<String> result = new ArrayList<>(size);
        // ignore subdirectories by considering only instances of CloudBlockBlob
        lbs.getResults().stream().filter(CloudBlockBlob.class::isInstance).forEach(item -> {
            CloudBlockBlob blob = (CloudBlockBlob) item;
            addTo(result, blob.getName().substring(prefixLength), () -> blob.getProperties().getLength());
        });
        return result;
    }

}
