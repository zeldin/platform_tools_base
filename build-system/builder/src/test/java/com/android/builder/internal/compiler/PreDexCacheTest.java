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

package com.android.builder.internal.compiler;

import static com.android.SdkConstants.FN_AAPT;
import static com.android.SdkConstants.FN_AIDL;
import static com.android.SdkConstants.FN_BCC_COMPAT;
import static com.android.SdkConstants.FN_DX;
import static com.android.SdkConstants.FN_DX_JAR;
import static com.android.SdkConstants.FN_RENDERSCRIPT;
import static com.android.SdkConstants.FN_ZIPALIGN;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.DexOptions;
import com.android.ide.common.internal.CommandLineRunner;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.FullRevision;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class PreDexCacheTest extends TestCase {

    private static final String DEX_DATA = "**";

    /**
     * Override the command line runner to intercept the call to dex and replace it
     * with something else.
     */
    private static class FakeCommandLineRunner extends CommandLineRunner {

        public FakeCommandLineRunner(ILogger logger) {
            super(logger);
        }

        @Override
        public void runCmdLine(@NonNull String[] command,
                @Nullable Map<String, String> envVariableMap)
                throws IOException, InterruptedException, LoggedErrorException {
            // small delay to test multi-threading.
            Thread.sleep(1000);

            // input file is the last file in the command
            File input = new File(command[command.length - 1]);
            if (!input.isFile()) {
                throw new FileNotFoundException(input.getPath());
            }

            // loop on the command to find --output
            String output = null;
            for (int i = 0 ; i < command.length ; i++) {
                if ("--output".equals(command[i])) {
                    output = command[i+1];
                    break;
                }
            }

            if (output == null) {
                throw new IOException("Failed to find output in dex commands");
            }

            // read the source content
            List<String> lines = Files.readLines(input, Charsets.UTF_8);

            // modify the lines
            List<String> dexedLines = Lists.newArrayListWithCapacity(lines.size());
            for (String line : lines) {
                dexedLines.add(DEX_DATA + line + DEX_DATA);
            }

            // combine the lines
            String content = Joiner.on('\n').join(dexedLines);

            // write it
            Files.write(content, new File(output), Charsets.UTF_8);
        }
    }

    /**
     * Override the command line runner to simulate error during the dexing
     */
    private static class FakeCommandLineRunner2 extends CommandLineRunner {

        public FakeCommandLineRunner2(ILogger logger) {
            super(logger);
        }

        @Override
        public void runCmdLine(@NonNull String[] command,
                @Nullable Map<String, String> envVariableMap)
                throws IOException, InterruptedException, LoggedErrorException {
            Thread.sleep(1000);
            throw new IOException("foo");
        }
    }

    private static class FakeDexOptions implements DexOptions {

        @Override
        public boolean getIncremental() {
            return false;
        }

        @Override
        public boolean getPreDexLibraries() {
            return false;
        }

        @Override
        public boolean getJumboMode() {
            return false;
        }

        @Override
        public String getJavaMaxHeapSize() {
            return null;
        }

        @Override
        public int getThreadCount() {
            return 1;
        }
    }

    private BuildToolInfo mBuildToolInfo;


    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mBuildToolInfo = getBuildToolInfo();
    }

    @Override
    protected void tearDown() throws Exception {
        File toolFolder = mBuildToolInfo.getLocation();
        deleteFolder(toolFolder);

        PreDexCache.getCache().clear(null, null);

        super.tearDown();
    }

    public void testSinglePreDexLibrary() throws IOException, LoggedErrorException, InterruptedException {
        String content = "Some Content";
        File input = createInputFile(content);

        File output = File.createTempFile("predex", ".jar");
        output.deleteOnExit();

        PreDexCache.getCache().preDexLibrary(
                input, output,
                false /*multidex*/,
                new FakeDexOptions(), mBuildToolInfo,
                false /*verbose*/, new FakeCommandLineRunner(new StdLogger(StdLogger.Level.INFO)));

        checkOutputFile(content, output);
    }

    public void testThreadedPreDexLibrary() throws IOException, InterruptedException {
        String content = "Some Content";
        final File input = createInputFile(content);
        input.deleteOnExit();

        Thread[] threads = new Thread[3];
        final File[] outputFiles = new File[threads.length];

        final CommandLineRunner clr = new FakeCommandLineRunner(new StdLogger(StdLogger.Level.INFO));
        final DexOptions dexOptions = new FakeDexOptions();

        for (int i = 0 ; i < threads.length ; i++) {
            final int ii = i;
            threads[i] = new Thread() {
                @Override
                public void run() {
                    try {
                        File output = File.createTempFile("predex", ".jar");
                        output.deleteOnExit();
                        outputFiles[ii] = output;

                        PreDexCache.getCache().preDexLibrary(
                                input, output,
                                false /*multidex*/,
                                dexOptions, mBuildToolInfo, false /*verbose*/, clr);
                    } catch (Exception ignored) {

                    }
                }
            };

            threads[i].start();
        }

        // wait on the threads.
        for (Thread thread : threads) {
            thread.join();
        }

        // check the output.
        for (File outputFile : outputFiles) {
            checkOutputFile(content, outputFile);
        }

        // now check the cache
        PreDexCache cache = PreDexCache.getCache();
        assertEquals(1, cache.getMisses());
        assertEquals(threads.length - 1, cache.getHits());
    }

    public void testThreadedPreDexLibraryWithError() throws IOException, InterruptedException {
        String content = "Some Content";
        final File input = createInputFile(content);
        input.deleteOnExit();

        Thread[] threads = new Thread[3];
        final File[] outputFiles = new File[threads.length];

        final CommandLineRunner clr = new FakeCommandLineRunner(new StdLogger(StdLogger.Level.INFO));
        final CommandLineRunner clrWithError = new FakeCommandLineRunner2(new StdLogger(StdLogger.Level.INFO));
        final DexOptions dexOptions = new FakeDexOptions();

        final AtomicInteger threadDoneCount = new AtomicInteger();

        for (int i = 0 ; i < threads.length ; i++) {
            final int ii = i;
            threads[i] = new Thread() {
                @Override
                public void run() {
                    try {
                        File output = File.createTempFile("predex", ".jar");
                        output.deleteOnExit();
                        outputFiles[ii] = output;

                        PreDexCache.getCache().preDexLibrary(
                                input, output,
                                false /*multidex*/,
                                dexOptions, mBuildToolInfo, false /*verbose*/,
                                ii == 0 ? clrWithError : clr);
                    } catch (Exception ignored) {

                    }
                    threadDoneCount.incrementAndGet();
                }
            };

            threads[i].start();
        }

        // wait on the threads, long enough but stop after a while
        for (Thread thread : threads) {
            thread.join(5000);
        }

        // if the test fail, we'll have two threads still blocked on the countdownlatch.
        assertEquals(3, threadDoneCount.get());
    }


    public void testReload() throws IOException, LoggedErrorException, InterruptedException {
        final CommandLineRunner clr = new FakeCommandLineRunner(new StdLogger(StdLogger.Level.INFO));
        final DexOptions dexOptions = new FakeDexOptions();

        // convert one file.
        String content = "Some Content";
        File input = createInputFile(content);

        File output = File.createTempFile("predex", ".jar");
        output.deleteOnExit();

        PreDexCache.getCache().preDexLibrary(
                input, output,
                false /*multidex*/,
                dexOptions, mBuildToolInfo, false /*verbose*/, clr);

        checkOutputFile(content, output);

        // store the cache
        File cacheXml = File.createTempFile("predex", ".xml");
        cacheXml.deleteOnExit();
        PreDexCache.getCache().clear(cacheXml, null);

        // reload.
        PreDexCache.getCache().load(cacheXml);

        // re-pre-dex into another file.
        File output2 = File.createTempFile("predex", ".jar");
        output2.deleteOnExit();

        PreDexCache.getCache().preDexLibrary(
                input, output2,
                false /*multidex*/,
                dexOptions, mBuildToolInfo, false /*verbose*/, clr);

        // check the output
        checkOutputFile(content, output2);

        // check the hit/miss
        PreDexCache cache = PreDexCache.getCache();
        assertEquals(0, cache.getMisses());
        assertEquals(1, cache.getHits());
    }

    private static File createInputFile(String content) throws IOException {
        File input = File.createTempFile("predex", ".jar");
        input.deleteOnExit();

        Files.write(content, input, Charsets.UTF_8);
        return input;
    }

    private static void checkOutputFile(String content, File output) throws IOException {
        List<String> lines = Files.readLines(output, Charsets.UTF_8);

        assertEquals(1, lines.size());
        assertEquals(DEX_DATA + content + DEX_DATA, lines.get(0));
    }

    /**
     * Create a fake build tool info where the dx tool actually exists (even if it's not used).
     */
    private static BuildToolInfo getBuildToolInfo() throws IOException {
        File toolDir = Files.createTempDir();

        // create a dx file.
        File dx = new File(toolDir, FN_DX);
        Files.write("dx!", dx, Charsets.UTF_8);

        return new BuildToolInfo(
                new FullRevision(1),
                toolDir,
                new File(toolDir, FN_AAPT),
                new File(toolDir, FN_AIDL),
                dx,
                new File(toolDir, FN_DX_JAR),
                new File(toolDir, FN_RENDERSCRIPT),
                new File(toolDir, "include"),
                new File(toolDir, "clang-include"),
                new File(toolDir, FN_BCC_COMPAT),
                new File(toolDir, "arm-linux-androideabi-ld"),
                new File(toolDir, "i686-linux-android-ld"),
                new File(toolDir, "mipsel-linux-android-ld"),
                new File(toolDir, FN_ZIPALIGN));
    }

    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else {
                    file.delete();
                }
            }
        }

        folder.delete();
    }
}
