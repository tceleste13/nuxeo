/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.blob;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.IOException;

import org.junit.Test;

/**
 * Copy and Move from another BlobStore has a different optimized code path.
 *
 * @since 2023.7
 */
public abstract class TestAbstractBlobStoreWithOptimizedCopy extends TestAbstractBlobStore {

    @Test
    public void testCopyIsOptimized() {
        BlobProvider srcProvider = blobManager.getBlobProvider("other");
        BlobStore srcStore = ((BlobStoreBlobProvider) srcProvider).store; // no need for unwrap
        assertTrue(bs.copyBlobIsOptimized(srcStore));
        srcStore = new InMemoryBlobStore("mem", new KeyStrategyDigest("MD5"));
        assertFalse(bs.copyBlobIsOptimized(srcStore));
    }

    @Test
    public void testCopyFromBlobStore() throws IOException {
        testCopyOrMoveFromBlobStore(false);
    }

    @Test
    public void testMoveFromBlobStore() throws IOException {
        testCopyOrMoveFromBlobStore(true);
    }

    protected void testCopyOrMoveFromBlobStore(boolean atomicMove) throws IOException {
        assumeFalse("Transactional blob providers don't support atomic optimized move/copy", bp.isTransactional());
        BlobProvider srcProvider = blobManager.getBlobProvider("other");
        BlobStore srcStore = ((BlobStoreBlobProvider) srcProvider).store;
        String key1 = useDeDuplication() ? FOO_MD5 : ID1;
        String key2 = useDeDuplication() ? key1 : ID2;
        assertNull(bs.copyOrMoveBlob(key2, srcStore, key1, atomicMove));
        assertEquals(key1, srcStore.writeBlob(blobContext(ID1, FOO)));
        String key3 = bs.copyOrMoveBlob(key2, srcStore, key1, atomicMove);
        assertEquals(key2, key3);
        assertBlob(bs, key2, FOO);
        if (atomicMove) {
            assertNoBlob(srcStore, key1);
        } else {
            assertBlob(srcStore, key1, FOO);
        }
    }
}
