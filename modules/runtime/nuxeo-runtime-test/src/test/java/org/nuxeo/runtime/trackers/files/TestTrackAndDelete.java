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

package org.nuxeo.runtime.trackers.files;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import java.io.File;
import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

/**
 * Asserts and shows the behavior of {@link Framework#trackFile(File, Object)}. This will fire a
 * {@link org.nuxeo.runtime.trackers.files.FileEvent} handled by a
 * {@link org.nuxeo.runtime.trackers.files.FileEventHandler}
 *
 * @since 2023.8
 */
@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
@Ignore(value = "This is for educational purpose only.")
public class TestTrackAndDelete {

    @Test
    public void canTrackAndDeleteFile() throws IOException {
        // Init a file on disk.
        var file = File.createTempFile("wipe", "out");

        // Make a marker, any object but a String is OK.
        var marker = new Object();
        Framework.trackFile(file, marker);

        // Null the reference, so it is eligible for JVM GC.
        marker = null; // NOSONAR
        System.gc(); // NOSONAR

        // A small delay is necessary for the tracker to catch up with the marker deletion.
        await().atMost(5, SECONDS).until(() -> !file.exists());
    }
}
