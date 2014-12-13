/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.internal.LintGradleClient
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.model.ModelBuilder
import com.android.builder.model.AndroidProject
import com.android.builder.model.Variant
import com.android.tools.lint.LintCliFlags
import com.android.tools.lint.Reporter
import com.android.tools.lint.Warning
import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.checks.GradleDetector
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Severity
import com.google.common.collect.Maps
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

public class Lint extends DefaultTask {
    @NonNull private BasePlugin mPlugin
    @Nullable private String mVariantName
    private boolean mFatalOnly

    public void setPlugin(@NonNull BasePlugin plugin) {
        mPlugin = plugin
    }

    public void setVariantName(@NonNull String variantName) {
        mVariantName = variantName
    }

    public void setFatalOnly(boolean fatalOnly) {
        mFatalOnly = fatalOnly
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    @TaskAction
    public void lint() {
        assert project == mPlugin.getProject()

        def modelProject = createAndroidProject(project)
        if (mVariantName != null) {
            lintSingleVariant(modelProject, mVariantName)
        } else {
            lintAllVariants(modelProject)
        }
    }

    /**
     * Runs lint individually on all the variants, and then compares the results
     * across variants and reports these
     */
    public void lintAllVariants(@NonNull AndroidProject modelProject) {
        Map<Variant,List<Warning>> warningMap = Maps.newHashMap()
        for (Variant variant : modelProject.getVariants()) {
            try {
                List<Warning> warnings = runLint(modelProject, variant.getName(), false)
                warningMap.put(variant, warnings)
            } catch (IOException e) {
                throw new GradleException("Invalid arguments.", e)
            }
        }

        // Compute error matrix
        def quiet = mPlugin.getExtension().lintOptions.quiet


        for (Map.Entry<Variant,List<Warning>> entry : warningMap.entrySet()) {
            def variant = entry.getKey()
            def warnings = entry.getValue()
            if (!mFatalOnly && !quiet) {
                println "Ran lint on variant " + variant.getName() + ": " + warnings.size() +
                        " issues found"
            }
        }

        List<Warning> mergedWarnings = LintGradleClient.merge(warningMap, modelProject)
        int errorCount = 0
        int warningCount = 0
        for (Warning warning : mergedWarnings) {
            if (warning.severity == Severity.ERROR || warning.severity == Severity.FATAL) {
                errorCount++
            } else if (warning.severity == Severity.WARNING) {
                warningCount++
            }
        }

        IssueRegistry registry = new BuiltinIssueRegistry()
        LintCliFlags flags = new LintCliFlags()
        LintGradleClient client = new LintGradleClient(registry, flags, mPlugin, modelProject,
                null)
        syncOptions(mPlugin.getExtension().lintOptions, client, flags, null, project, true,
                mFatalOnly)

        for (Reporter reporter : flags.getReporters()) {
            reporter.write(errorCount, warningCount, mergedWarnings)
        }

        if (flags.isSetExitCode() && errorCount > 0) {
            abort()
        }
    }

    private void abort() {
        def message;
        if (mFatalOnly) {
            message = "" +
                    "Lint found fatal errors while assembling a release target.\n" +
                    "\n" +
                    "To proceed, either fix the issues identified by lint, or modify your build script as follows:\n" +
                    "...\n" +
                    "android {\n" +
                    "    lintOptions {\n" +
                    "        checkReleaseBuilds false\n" +
                    "        // Or, if you prefer, you can continue to check for errors in release builds,\n" +
                    "        // but continue the build even when errors are found:\n" +
                    "        abortOnError false\n" +
                    "    }\n" +
                    "}\n" +
                    "..."
                    ""
        } else {
            message = "" +
                    "Lint found errors in the project; aborting build.\n" +
                    "\n" +
                    "Fix the issues identified by lint, or add the following to your build script to proceed with errors:\n" +
                    "...\n" +
                    "android {\n" +
                    "    lintOptions {\n" +
                    "        abortOnError false\n" +
                    "    }\n" +
                    "}\n" +
                    "..."
        }
        throw new GradleException(message);
    }

    /**
     * Runs lint on a single specified variant
     */
    public void lintSingleVariant(@NonNull AndroidProject modelProject, String variantName) {
        runLint(modelProject, variantName, true)
    }

    /** Runs lint on the given variant and returns the set of warnings */
    private List<Warning> runLint(
            @NonNull AndroidProject modelProject,
            @NonNull String variantName,
            boolean report) {
        IssueRegistry registry = createIssueRegistry()
        LintCliFlags flags = new LintCliFlags()
        LintGradleClient client = new LintGradleClient(registry, flags, mPlugin, modelProject,
                variantName)
        def options = mPlugin.getExtension().lintOptions
        if (mFatalOnly) {
            if (!options.isCheckReleaseBuilds()) {
                return
            }
            flags.setFatalOnly(true)
        }
        syncOptions(options, client, flags, variantName, project, report, mFatalOnly)
        if (!report || mFatalOnly) {
            flags.setQuiet(true)
        }

        List<Warning> warnings;
        try {
            warnings = client.run(registry)
        } catch (IOException e) {
            throw new GradleException("Invalid arguments.", e)
        }

        if (report && client.haveErrors() && flags.isSetExitCode()) {
            abort()
        }

        return warnings;
    }

    private static syncOptions(
            @NonNull LintOptions options,
            @NonNull LintGradleClient client,
            @NonNull LintCliFlags flags,
            @NonNull String variantName,
            @NonNull Project project,
            boolean report,
            boolean fatalOnly) {
        options.syncTo(client, flags, variantName, project, report)

        if (fatalOnly || flags.quiet) {
            for (Reporter reporter : flags.getReporters()) {
                reporter.setDisplayEmpty(false)
            }
        }
    }

    private static AndroidProject createAndroidProject(@NonNull Project gradleProject) {
        String modelName = AndroidProject.class.getName()
        ModelBuilder builder = new ModelBuilder()
        return (AndroidProject) builder.buildAll(modelName, gradleProject)
    }

    private static BuiltinIssueRegistry createIssueRegistry() {
        return new LintGradleIssueRegistry()
    }

    // Issue registry when Lint is run inside Gradle: we replace the Gradle
    // detector with a local implementation which directly references Groovy
    // for parsing. In Studio on the other hand, the implementation is replaced
    // by a PSI-based check. (This is necessary for now since we don't have a
    // tool-agnostic API for the Groovy AST and we don't want to add a 6.3MB dependency
    // on Groovy itself quite yet.
    public static class LintGradleIssueRegistry extends BuiltinIssueRegistry {
        private boolean mInitialized;

        public LintGradleIssueRegistry() {
        }

        @NonNull
        @Override
        public List<Issue> getIssues() {
            List<Issue> issues = super.getIssues();
            if (!mInitialized) {
                mInitialized = true;
                for (Issue issue : issues) {
                    if (issue.getImplementation().getDetectorClass() == GradleDetector.class) {
                        issue.setImplementation(GroovyGradleDetector.IMPLEMENTATION);
                    }
                }
            }

            return issues;
        }
    }
}
