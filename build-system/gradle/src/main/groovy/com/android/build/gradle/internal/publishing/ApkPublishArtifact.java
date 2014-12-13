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

package com.android.build.gradle.internal.publishing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.tasks.OutputFileTask;

import org.gradle.api.Task;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

/**
 * custom implementation of PublishArtifact for published APKs.
 */
public class ApkPublishArtifact implements PublishArtifact {

    @NonNull
    private final String name;

    @Nullable
    private final String classifier;

    @NonNull
    private final OutputFileTask task;
    @NonNull
    private final TaskDependency taskDependency;

    private static final class DefaultTaskDependency implements TaskDependency {

        @NonNull
        private final Set<Task> tasks;

        DefaultTaskDependency(@NonNull Task task) {
            this.tasks = Collections.singleton(task);
        }

        @Override
        public Set<? extends Task> getDependencies(Task task) {
            return tasks;
        }
    }

    public ApkPublishArtifact(
            @NonNull String name,
            @Nullable String classifier,
            @NonNull OutputFileTask task) {
        this.name = name;
        this.classifier = classifier;
        this.task = task;
        this.taskDependency = new DefaultTaskDependency((Task) task);
    }


    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    public String getExtension() {
        return "apk";
    }

    @Override
    public String getType() {
        return "apk";
    }

    @Override
    public String getClassifier() {
        return classifier;
    }

    @Override
    public File getFile() {
        return task.getOutputFile();
    }

    @Override
    public Date getDate() {
        return null;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return taskDependency;
    }
}
