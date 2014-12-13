/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.Variant;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Project;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("javadoc")
public class TranslationDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new TranslationDetector();
    }

    @Override
    protected boolean includeParentPath() {
        return true;
    }

    public void testTranslation() throws Exception {
        TranslationDetector.sCompleteRegions = false;
        assertEquals(
            // Sample files from the Home app
            "res/values/strings.xml:20: Error: \"show_all_apps\" is not translated in \"nl-rNL\" (Dutch: Netherlands) [MissingTranslation]\n" +
            "    <string name=\"show_all_apps\">All</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~\n" +
            "res/values/strings.xml:23: Error: \"menu_wallpaper\" is not translated in \"nl-rNL\" (Dutch: Netherlands) [MissingTranslation]\n" +
            "    <string name=\"menu_wallpaper\">Wallpaper</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/values/strings.xml:25: Error: \"menu_settings\" is not translated in \"cs\" (Czech), \"de-rDE\" (German: Germany), \"es\" (Spanish), \"es-rUS\" (Spanish: United States), \"nl-rNL\" (Dutch: Netherlands) [MissingTranslation]\n" +
            "    <string name=\"menu_settings\">Settings</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~\n" +
            "res/values-cs/arrays.xml:3: Error: \"security_questions\" is translated here but not found in default locale [ExtraTranslation]\n" +
            "  <string-array name=\"security_questions\">\n" +
            "                ~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    res/values-es/strings.xml:12: Also translated here\n" +
            "res/values-de-rDE/strings.xml:11: Error: \"continue_skip_label\" is translated here but not found in default locale [ExtraTranslation]\n" +
            "    <string name=\"continue_skip_label\">\"Weiter\"</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "5 errors, 0 warnings\n",

            lintProject(
                 "res/values/strings.xml",
                 "res/values-cs/strings.xml",
                 "res/values-de-rDE/strings.xml",
                 "res/values-es/strings.xml",
                 "res/values-es-rUS/strings.xml",
                 "res/values-land/strings.xml",
                 "res/values-cs/arrays.xml",
                 "res/values-es/donottranslate.xml",
                 "res/values-nl-rNL/strings.xml"));
    }

    public void testTranslationWithCompleteRegions() throws Exception {
        TranslationDetector.sCompleteRegions = true;
        assertEquals(
            // Sample files from the Home app
            "res/values/strings.xml:19: Error: \"home_title\" is not translated in \"es-rUS\" (Spanish: United States) [MissingTranslation]\n" +
            "    <string name=\"home_title\">Home Sample</string>\n" +
            "            ~~~~~~~~~~~~~~~~~\n" +
            "res/values/strings.xml:20: Error: \"show_all_apps\" is not translated in \"es-rUS\" (Spanish: United States), \"nl-rNL\" (Dutch: Netherlands) [MissingTranslation]\n" +
            "    <string name=\"show_all_apps\">All</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~\n" +
            "res/values/strings.xml:23: Error: \"menu_wallpaper\" is not translated in \"es-rUS\" (Spanish: United States), \"nl-rNL\" (Dutch: Netherlands) [MissingTranslation]\n" +
            "    <string name=\"menu_wallpaper\">Wallpaper</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~\n" +
            "res/values/strings.xml:25: Error: \"menu_settings\" is not translated in \"cs\" (Czech), \"de-rDE\" (German: Germany), \"es-rUS\" (Spanish: United States), \"nl-rNL\" (Dutch: Netherlands) [MissingTranslation]\n" +
            "    <string name=\"menu_settings\">Settings</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~\n" +
            "res/values/strings.xml:29: Error: \"wallpaper_instructions\" is not translated in \"es-rUS\" (Spanish: United States) [MissingTranslation]\n" +
            "    <string name=\"wallpaper_instructions\">Tap picture to set portrait wallpaper</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "    res/values-land/strings.xml:19: <No location-specific message\n" +
            "res/values-de-rDE/strings.xml:11: Error: \"continue_skip_label\" is translated here but not found in default locale [ExtraTranslation]\n" +
            "    <string name=\"continue_skip_label\">\"Weiter\"</string>\n" +
            "            ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
            "6 errors, 0 warnings\n",

            lintProject(
                 "res/values/strings.xml",
                 "res/values-cs/strings.xml",
                 "res/values-de-rDE/strings.xml",
                 "res/values-es-rUS/strings.xml",
                 "res/values-land/strings.xml",
                 "res/values-nl-rNL/strings.xml"));
    }

    public void testHandleBom() throws Exception {
        // This isn't really testing translation detection; it's just making sure that the
        // XML parser doesn't bomb on BOM bytes (byte order marker) at the beginning of
        // the XML document
        assertEquals(
            "No warnings.",
            lintProject(
                 "res/values-de/strings.xml"
            ));
    }

    public void testTranslatedArrays() throws Exception {
        TranslationDetector.sCompleteRegions = true;
        assertEquals(
            "No warnings.",

            lintProject(
                 "res/values/translatedarrays.xml",
                 "res/values-cs/translatedarrays.xml"));
    }

    public void testTranslationSuppresss() throws Exception {
        TranslationDetector.sCompleteRegions = false;
        assertEquals(
            "No warnings.",

            lintProject(
                    "res/values/strings_ignore.xml=>res/values/strings.xml",
                    "res/values-es/strings_ignore.xml=>res/values-es/strings.xml",
                    "res/values-nl-rNL/strings.xml=>res/values-nl-rNL/strings.xml"));
    }

    public void testMixedTranslationArrays() throws Exception {
        // See issue http://code.google.com/p/android/issues/detail?id=29263
        assertEquals(
                "No warnings.",

                lintProject(
                        "res/values/strings3.xml=>res/values/strings.xml",
                        "res/values-fr/strings.xml=>res/values-fr/strings.xml"));
    }

    public void testLibraryProjects() throws Exception {
        // If a library project provides additional locales, that should not force
        // the main project to include all those translations
        assertEquals(
            "No warnings.",

             lintProject(
                 // Master project
                 "multiproject/main-manifest.xml=>AndroidManifest.xml",
                 "multiproject/main.properties=>project.properties",
                 "res/values/strings2.xml",

                 // Library project
                 "multiproject/library-manifest.xml=>../LibraryProject/AndroidManifest.xml",
                 "multiproject/library.properties=>../LibraryProject/project.properties",

                 "res/values/strings.xml=>../LibraryProject/res/values/strings.xml",
                 "res/values-cs/strings.xml=>../LibraryProject/res/values-cs/strings.xml",
                 "res/values-cs/strings.xml=>../LibraryProject/res/values-de/strings.xml",
                 "res/values-cs/strings.xml=>../LibraryProject/res/values-nl/strings.xml"
             ));
    }

    public void testNonTranslatable1() throws Exception {
        TranslationDetector.sCompleteRegions = true;
        assertEquals(
            "res/values-nb/nontranslatable.xml:3: Error: The resource string \"dummy\" has been marked as translatable=\"false\" [ExtraTranslation]\n" +
            "    <string name=\"dummy\">Ignore Me</string>\n" +
            "            ~~~~~~~~~~~~\n" +
            "1 errors, 0 warnings\n" +
            "",

            lintProject("res/values/nontranslatable.xml",
                    "res/values/nontranslatable2.xml=>res/values-nb/nontranslatable.xml"));
    }

    public void testNonTranslatable2() throws Exception {
        TranslationDetector.sCompleteRegions = true;
        assertEquals(
            "res/values-nb/nontranslatable.xml:3: Error: Non-translatable resources should only be defined in the base values/ folder [ExtraTranslation]\n" +
            "    <string name=\"dummy\" translatable=\"false\">Ignore Me</string>\n" +
            "                         ~~~~~~~~~~~~~~~~~~~~\n" +
            "1 errors, 0 warnings\n" +
            "",

            lintProject("res/values/nontranslatable.xml=>res/values-nb/nontranslatable.xml"));
    }

    public void testSpecifiedLanguageOk() throws Exception {
        TranslationDetector.sCompleteRegions = false;
        assertEquals(
            "No warnings.",

            lintProject(
                 "res/values-es/strings.xml=>res/values-es/strings.xml",
                 "res/values-es-rUS/strings.xml"));
    }

    public void testSpecifiedLanguage() throws Exception {
        TranslationDetector.sCompleteRegions = false;
        assertEquals(
            "No warnings.",

            lintProject(
                 "res/values-es/strings_locale.xml=>res/values/strings.xml",
                 "res/values-es-rUS/strings.xml"));
    }

    public void testAnalytics() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=43070
        assertEquals(
                "No warnings.",

                lintProject(
                        "res/values/analytics.xml",
                        "res/values-es/donottranslate.xml" // to make app multilingual
                ));
    }

    public void testIssue33845() throws Exception {
        // See http://code.google.com/p/android/issues/detail?id=33845
        assertEquals(""
                + "res/values/strings.xml:5: Error: \"dateTimeFormat\" is not translated in \"de\" (German) [MissingTranslation]\n"
                + "    <string name=\"dateTimeFormat\">MM/dd/yyyy - HH:mm</string>\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                lintProject(
                        "locale33845/.classpath=>.classpath",
                        "locale33845/AndroidManifest.xml=>AndroidManifest.xml",
                        "locale33845/project.properties=>project.properties",
                        "locale33845/res/values/strings.xml=>res/values/strings.xml",
                        "locale33845/res/values-de/strings.xml=>res/values-de/strings.xml",
                        "locale33845/res/values-en-rGB/strings.xml=>res/values-en-rGB/strings.xml"
                ));
    }

    public void testIssue33845b() throws Exception {
        // Similar to issue 33845, but with some variations to the test data
        // See http://code.google.com/p/android/issues/detail?id=33845
        assertEquals("No warnings.",

                lintProject(
                        "locale33845/.classpath=>.classpath",
                        "locale33845/AndroidManifest.xml=>AndroidManifest.xml",
                        "locale33845/project.properties=>project.properties",
                        "locale33845/res/values/styles.xml=>res/values/styles.xml",
                        "locale33845/res/values/strings2.xml=>res/values/strings.xml",
                        "locale33845/res/values-en-rGB/strings2.xml=>res/values-en-rGB/strings.xml"
                ));
    }

    public void testEnglishRegionAndValuesAsEnglish1() throws Exception {
        // tools:locale=en in base folder
        // Regression test for https://code.google.com/p/android/issues/detail?id=75879
        assertEquals("No warnings.",

                lintProject(
                        "locale33845/res/values/strings3.xml=>res/values/strings.xml",
                        "locale33845/res/values-en-rGB/strings3.xml=>res/values-en-rGB/strings.xml"
                ));
    }

    public void testEnglishRegionAndValuesAsEnglish2() throws Exception {
        // No tools:locale specified in the base folder: *assume* English
        // Regression test for https://code.google.com/p/android/issues/detail?id=75879
        assertEquals(""
                + "res/values/strings.xml:5: Error: \"other\" is not translated in \"de-rDE\" (German: Germany) [MissingTranslation]\n"
                + "    <string name=\"other\">other</string>\n"
                + "            ~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                lintProject(
                        "locale33845/res/values/strings4.xml=>res/values/strings.xml",
                        // Flagged because it's not the default locale:
                        "locale33845/res/values-en-rGB/strings3.xml=>res/values-de-rDE/strings.xml",
                        // Not flagged because it's the implicit default locale
                        "locale33845/res/values-en-rGB/strings3.xml=>res/values-en-rGB/strings.xml"
                ));
    }

    public void testEnglishRegionAndValuesAsEnglish3() throws Exception {
        // tools:locale=de in base folder
        // Regression test for https://code.google.com/p/android/issues/detail?id=75879
        assertEquals("No warnings.",

                lintProject(
                        "locale33845/res/values/strings5.xml=>res/values/strings.xml",
                        "locale33845/res/values-en-rGB/strings3.xml=>res/values-de-rDE/strings.xml"
                ));
    }

    public void testResConfigs() throws Exception {
        TranslationDetector.sCompleteRegions = false;
        assertEquals(""
                + "res/values/strings.xml:25: Error: \"menu_settings\" is not translated in \"cs\" (Czech), \"de-rDE\" (German: Germany) [MissingTranslation]\n"
                + "    <string name=\"menu_settings\">Settings</string>\n"
                + "            ~~~~~~~~~~~~~~~~~~~~\n"
                + "res/values-cs/arrays.xml:3: Error: \"security_questions\" is translated here but not found in default locale [ExtraTranslation]\n"
                + "  <string-array name=\"security_questions\">\n"
                + "                ~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "    res/values-es/strings.xml:12: Also translated here\n"
                + "res/values-de-rDE/strings.xml:11: Error: \"continue_skip_label\" is translated here but not found in default locale [ExtraTranslation]\n"
                + "    <string name=\"continue_skip_label\">\"Weiter\"</string>\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "3 errors, 0 warnings\n",

                lintProject(
                        "res/values/strings.xml",
                        "res/values-cs/strings.xml",
                        "res/values-de-rDE/strings.xml",
                        "res/values-es/strings.xml",
                        "res/values-es-rUS/strings.xml",
                        "res/values-land/strings.xml",
                        "res/values-cs/arrays.xml",
                        "res/values-es/donottranslate.xml",
                        "res/values-nl-rNL/strings.xml"));
    }

    @Override
    protected TestLintClient createClient() {
        if (!getName().startsWith("testResConfigs")) {
            return super.createClient();
        }

        // Set up a mock project model for the resource configuration test(s)
        // where we provide a subset of densities to be included

        return new TestLintClient() {
            @NonNull
            @Override
            protected Project createProject(@NonNull File dir, @NonNull File referenceDir) {
                return new Project(this, dir, referenceDir) {
                    @Override
                    public boolean isGradleProject() {
                        return true;
                    }

                    @Nullable
                    @Override
                    public AndroidProject getGradleProjectModel() {
                        /*
                        Simulate variant freeBetaDebug in this setup:
                            defaultConfig {
                                ...
                                resConfigs "cs"
                            }
                            flavorDimensions  "pricing", "releaseType"
                            productFlavors {
                                beta {
                                    flavorDimension "releaseType"
                                    resConfig "en", "de"
                                    resConfigs "nodpi", "hdpi"
                                }
                                normal { flavorDimension "releaseType" }
                                free { flavorDimension "pricing" }
                                paid { flavorDimension "pricing" }
                            }
                         */
                        ProductFlavor flavorFree = createNiceMock(ProductFlavor.class);
                        expect(flavorFree.getName()).andReturn("free").anyTimes();
                        expect(flavorFree.getResourceConfigurations())
                                .andReturn(Collections.<String>emptyList()).anyTimes();
                        replay(flavorFree);

                        ProductFlavor flavorNormal = createNiceMock(ProductFlavor.class);
                        expect(flavorNormal.getName()).andReturn("normal").anyTimes();
                        expect(flavorNormal.getResourceConfigurations())
                                .andReturn(Collections.<String>emptyList()).anyTimes();
                        replay(flavorNormal);

                        ProductFlavor flavorPaid = createNiceMock(ProductFlavor.class);
                        expect(flavorPaid.getName()).andReturn("paid").anyTimes();
                        expect(flavorPaid.getResourceConfigurations())
                                .andReturn(Collections.<String>emptyList()).anyTimes();
                        replay(flavorPaid);

                        ProductFlavor flavorBeta = createNiceMock(ProductFlavor.class);
                        expect(flavorBeta.getName()).andReturn("beta").anyTimes();
                        List<String> resConfigs = Arrays.asList("hdpi", "en", "de", "nodpi");
                        expect(flavorBeta.getResourceConfigurations()).andReturn(resConfigs).anyTimes();
                        replay(flavorBeta);

                        ProductFlavor defaultFlavor = createNiceMock(ProductFlavor.class);
                        expect(defaultFlavor.getName()).andReturn("main").anyTimes();
                        expect(defaultFlavor.getResourceConfigurations()).andReturn(
                                Collections.singleton("cs")).anyTimes();
                        replay(defaultFlavor);

                        ProductFlavorContainer containerBeta =
                                createNiceMock(ProductFlavorContainer.class);
                        expect(containerBeta.getProductFlavor()).andReturn(flavorBeta).anyTimes();
                        replay(containerBeta);

                        ProductFlavorContainer containerFree =
                                createNiceMock(ProductFlavorContainer.class);
                        expect(containerFree.getProductFlavor()).andReturn(flavorFree).anyTimes();
                        replay(containerFree);

                        ProductFlavorContainer containerPaid =
                                createNiceMock(ProductFlavorContainer.class);
                        expect(containerPaid.getProductFlavor()).andReturn(flavorPaid).anyTimes();
                        replay(containerPaid);

                        ProductFlavorContainer containerNormal =
                                createNiceMock(ProductFlavorContainer.class);
                        expect(containerNormal.getProductFlavor()).andReturn(flavorNormal).anyTimes();
                        replay(containerNormal);

                        ProductFlavorContainer defaultContainer =
                                createNiceMock(ProductFlavorContainer.class);
                        expect(defaultContainer.getProductFlavor()).andReturn(defaultFlavor).anyTimes();
                        replay(defaultContainer);

                        List<ProductFlavorContainer> containers = Arrays.asList(
                                containerPaid, containerFree, containerNormal, containerBeta
                        );

                        AndroidProject project = createNiceMock(AndroidProject.class);
                        expect(project.getProductFlavors()).andReturn(containers).anyTimes();
                        expect(project.getDefaultConfig()).andReturn(defaultContainer).anyTimes();
                        replay(project);
                        return project;
                    }

                    @Nullable
                    @Override
                    public Variant getCurrentVariant() {
                        List<String> productFlavorNames = Arrays.asList("free", "beta");
                        Variant mock = createNiceMock(Variant.class);
                        expect(mock.getProductFlavors()).andReturn(productFlavorNames).anyTimes();
                        replay(mock);
                        return mock;
                    }
                };
            }
        };
    }
}
