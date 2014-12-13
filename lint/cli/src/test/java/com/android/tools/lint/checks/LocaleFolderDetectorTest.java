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


package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class LocaleFolderDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new LocaleFolderDetector();
    }

    public void test() throws Exception {
        assertEquals(""
            + "res/values-he: Warning: The locale folder \"he\" should be called \"iw\" instead; see the java.util.Locale documentation [LocaleFolder]\n"
            + "res/values-id: Warning: The locale folder \"id\" should be called \"in\" instead; see the java.util.Locale documentation [LocaleFolder]\n"
            + "res/values-yi: Warning: The locale folder \"yi\" should be called \"ji\" instead; see the java.util.Locale documentation [LocaleFolder]\n"
            + "0 errors, 3 warnings\n",

            lintProject(
                    "res/values/strings.xml",
                    "res/values/strings.xml=>res/values-no/strings.xml",
                    "res/values/strings.xml=>res/values-he/strings.xml",
                    "res/values/strings.xml=>res/values-id/strings.xml",
                    "res/values/strings.xml=>res/values-yi/strings.xml")
        );
    }
}