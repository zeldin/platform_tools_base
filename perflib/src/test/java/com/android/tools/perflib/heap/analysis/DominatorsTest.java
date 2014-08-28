/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.perflib.heap.analysis;

import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.HprofParser;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.heap.io.MemoryMappedFileBuffer;

import junit.framework.TestCase;

import java.io.File;
import java.util.Map;

public class DominatorsTest extends TestCase {

    private Snapshot mSnapshot;

    private Map<Instance, Instance> mDominators;

    public void testSimpleGraph() {
        mSnapshot = new SnapshotBuilder(6)
                .addReferences(1, 2, 3)
                .addReferences(2, 4, 6)
                .addReferences(3, 4, 5)
                .addReferences(4, 6)
                .addRoot(1)
                .getSnapshot();

        mDominators = Dominators.getDominatorMap(mSnapshot);
        assertEquals(6, mDominators.size());
        assertDominates(1, 2);
        assertDominates(1, 3);
        assertDominates(1, 4);
        assertDominates(1, 6);
        assertDominates(3, 5);
    }

    public void testCyclicGraph() {
        mSnapshot = new SnapshotBuilder(4)
                .addReferences(1, 2, 3, 4)
                .addReferences(2, 3)
                .addReferences(3, 4)
                .addReferences(4, 2)
                .addRoot(1)
                .getSnapshot();

        mDominators = Dominators.getDominatorMap(mSnapshot);
        assertEquals(4, mDominators.size());
        assertDominates(1, 2);
        assertDominates(1, 3);
        assertDominates(1, 4);
    }

    public void testMultipleRoots() {
        mSnapshot = new SnapshotBuilder(6)
                .addReferences(1, 3)
                .addReferences(2, 4)
                .addReferences(3, 5)
                .addReferences(4, 5)
                .addReferences(5, 6)
                .addRoot(1)
                .addRoot(2)
                .getSnapshot();

        mDominators = Dominators.getDominatorMap(mSnapshot);
        assertEquals(6, mDominators.size());
        assertDominates(1, 3);
        assertDominates(2, 4);
        // Node 5 is reachable via both roots, neither of which can be the sole dominator.
        assertEquals(mSnapshot.SENTINEL_ROOT, mDominators.get(mSnapshot.findReference(5)));
        assertDominates(5, 6);
    }

    public void testSampleHprof() throws Exception {
        File file = new File(ClassLoader.getSystemResource("dialer.android-hprof").getFile());
        Snapshot snapshot = (new HprofParser(new MemoryMappedFileBuffer(file))).parse();
        Map<Instance, Instance> dominators = snapshot.computeDominatorMap();

        // TODO: Double-check this data
        assertEquals(29598, dominators.size());

        // An object reachable via two GC roots, a JNI global and a Thread.
        Instance instance = snapshot.findReference(0xB0EDFFA0);
        assertEquals(Snapshot.SENTINEL_ROOT, dominators.get(instance));

        snapshot.computeRetainedSizes();
        // The largest object in our sample hprof belongs to the zygote
        ClassObj htmlParser = snapshot.findClass("android.text.Html$HtmlParser");
        assertEquals(116468, htmlParser.getRetainedSize(snapshot.getHeap("zygote")));
        assertEquals(0, htmlParser.getRetainedSize(snapshot.getHeap("app")));

        // One of the bigger objects in the app heap
        ClassObj activityThread = snapshot.findClass("android.app.ActivityThread");
        assertEquals(237, activityThread.getRetainedSize(snapshot.getHeap("zygote")));
        assertEquals(576, activityThread.getRetainedSize(snapshot.getHeap("app")));
    }

    /**
     * Asserts that nodeA dominates nodeB in mHeap.
     */
    private void assertDominates(int nodeA, int nodeB) {
        assertEquals(mSnapshot.findReference(nodeA),
                mDominators.get(mSnapshot.findReference(nodeB)));
    }
}
