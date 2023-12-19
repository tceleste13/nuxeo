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

import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.junit.Assume.assumeTrue;
import static org.nuxeo.ecm.blob.azure.AzureBlobStoreConfiguration.AZURE_STORAGE_ACCESS_KEY_ENV_VAR;
import static org.nuxeo.ecm.blob.azure.AzureBlobStoreConfiguration.AZURE_STORAGE_ACCOUNT_ENV_VAR;

import java.io.IOException;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.blob.AbstractCloudBlobProviderFeature;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobManagerFeature;
import org.nuxeo.runtime.model.URLStreamRef;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;
import org.nuxeo.runtime.test.runner.RuntimeHarness;
import org.osgi.framework.Bundle;

/**
 * This feature deploys two {@link AzureBlobProvider} configured through environment variables and system properties.
 * <p>
 * The blob provider will load its configuration as below:
 * <ul>
 * <li>loads the property from the contribution
 * <li>loads the property from the system properties having {@link AzureBlobStoreConfiguration#SYSTEM_PROPERTY_PREFIX}
 * as prefix
 * </ul>
 * This feature will plug into this behavior and do:
 * <ul>
 * <li>load environment variables if any
 * <li>load test system properties if any
 * <li>put these properties into the contribution
 * </ul>
 * By default, the two {@link AzureBlobProvider} will have the same {@code accountName}, {@code accountKey}, and
 * {@code container}. The blob provider {@code test} will use {@code provider-test-TIMESTAMP/} as default
 * {@code prefix}, respectively {@code provider-other-TIMESTAMP/} for the blob provider {@code other}.
 * <p>
 * If a test needs a specific blob provider settings, it can deploy a partial contribution with these settings only, the
 * descriptor merge behavior will do the necessary.
 * <p>
 * The containers will be cleared before and after each test execution.
 *
 * @since 2023.6
 */
@Features(BlobManagerFeature.class)
@Deploy("org.nuxeo.ecm.core.storage.binarymanager.azure")
@Deploy("org.nuxeo.ecm.core.storage.binarymanager.azure.test")
public class AzureBlobProviderFeature extends AbstractCloudBlobProviderFeature {

    protected static final Logger log = LogManager.getLogger(AzureBlobProviderFeature.class);

    public static final String PREFIX_TEST = "nuxeo.test.azure.";

    public static final String ACCOUNT_KEY = PREFIX_TEST + AzureBlobStoreConfiguration.ACCOUNT_KEY_PROPERTY;

    public static final String BUCKET = PREFIX_TEST + AzureBlobStoreConfiguration.CONTAINER_PROPERTY;

    public static final String ACCOUNT_NAME = PREFIX_TEST + AzureBlobStoreConfiguration.ACCOUNT_NAME_PROPERTY;

    public static final String PREFIX_PROVIDER_TEST = PREFIX_TEST + "provider.test.";

    public static final String PREFIX_PROVIDER_OTHER = PREFIX_TEST + "provider.other.";

    public static final String PROVIDER_TEST_BUCKET = PREFIX_PROVIDER_TEST
            + AzureBlobStoreConfiguration.CONTAINER_PROPERTY;

    public static final String PROVIDER_TEST_BUCKET_PREFIX = PREFIX_PROVIDER_TEST
            + AzureBlobStoreConfiguration.PREFIX_PROPERTY;

    public static final String DEFAULT_PROVIDER_TEST_BUCKET_PREFIX = "provider-test";

    public static final String PROVIDER_OTHER_BUCKET = PREFIX_PROVIDER_OTHER
            + AzureBlobStoreConfiguration.CONTAINER_PROPERTY;

    public static final String PROVIDER_OTHER_BUCKET_PREFIX = PREFIX_PROVIDER_OTHER
            + AzureBlobStoreConfiguration.PREFIX_PROPERTY;

    public static final String DEFAULT_PROVIDER_OTHER_BUCKET_PREFIX = "provider-other";

    @SuppressWarnings("unchecked")
    @Override
    public void start(FeaturesRunner runner) {
        // configure global blob provider properties
        var accountName = configureProperty(ACCOUNT_NAME, sysEnv(AZURE_STORAGE_ACCOUNT_ENV_VAR), sysProp(ACCOUNT_NAME));
        var accountKey = configureProperty(ACCOUNT_KEY, sysEnv(AZURE_STORAGE_ACCESS_KEY_ENV_VAR), sysProp(ACCOUNT_KEY));
        // configure specific blob provider properties
        var testBucket = configureProperty(PROVIDER_TEST_BUCKET, sysProp(PROVIDER_TEST_BUCKET), sysProp(BUCKET));
        configureProperty(PROVIDER_TEST_BUCKET_PREFIX, unique(sysProp(PROVIDER_TEST_BUCKET_PREFIX).get()),
                unique(DEFAULT_PROVIDER_TEST_BUCKET_PREFIX));
        var otherBucket = configureProperty(PROVIDER_OTHER_BUCKET, sysProp(PROVIDER_OTHER_BUCKET),
                sysProp(PROVIDER_TEST_BUCKET), sysProp(BUCKET));
        configureProperty(PROVIDER_OTHER_BUCKET_PREFIX, unique(sysProp(PROVIDER_OTHER_BUCKET_PREFIX).get()),
                unique(DEFAULT_PROVIDER_OTHER_BUCKET_PREFIX));
        // check if tests can run
        assumeTrue("Azure accountName, accountKey and container are missing in test configuration",
                isNoneBlank(accountName, accountKey, testBucket, otherBucket));
        // deploy the test bundle after the properties have been set
        try {
            RuntimeHarness harness = runner.getFeature(RuntimeFeature.class).getHarness();
            Bundle bundle = harness.getOSGiAdapter()
                                   .getRegistry()
                                   .getBundle("org.nuxeo.ecm.core.storage.binarymanager.azure.test");
            URL url = bundle.getEntry("OSGI-INF/test-azure-config.xml");
            harness.getContext().deploy(new URLStreamRef(url));
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }
}
