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
package com.android.build.gradle.internal.tasks

import com.android.build.OutputFile
import com.android.build.gradle.api.ApkOutputFile
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.BaseVariantOutputData
import com.android.builder.core.VariantConfiguration
import com.android.builder.internal.InstallUtils
import com.android.builder.testing.ConnectedDeviceProvider
import com.android.builder.testing.api.DeviceConnector
import com.android.builder.testing.api.DeviceProvider
import com.android.ddmlib.IDevice
import com.android.ide.common.build.SplitOutputMatcher
import com.google.common.base.Joiner
import org.gradle.api.GradleException
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

/**
 * Task installing an app variant. It looks at connected device and install the best matching
 * variant output on each device.
 */
public class InstallVariantTask extends BaseTask {
    @InputFile
    File adbExe

    int timeOut = 0

    BaseVariantData<? extends BaseVariantOutputData> variantData
    InstallVariantTask() {
        this.getOutputs().upToDateWhen {
            logger.debug("Install task is always run.");
            false;
        }
    }

    @TaskAction
    void install() {
        DeviceProvider deviceProvider = new ConnectedDeviceProvider(getAdbExe())
        deviceProvider.init()

        VariantConfiguration variantConfig = variantData.variantConfiguration
        String variantName = variantConfig.fullName
        String projectName = plugin.project.name

        String serial = System.getenv("ANDROID_SERIAL");

        int successfulInstallCount = 0;

        for (DeviceConnector device : deviceProvider.getDevices()) {
            if (serial != null && !serial.equals(device.getSerialNumber())) {
                continue;
            }

            if (device.getState() != IDevice.DeviceState.UNAUTHORIZED) {
                if (InstallUtils.checkDeviceApiLevel(
                        device, variantConfig.minSdkVersion, plugin.logger, projectName,
                        variantName)) {

                    // now look for a matching output file
                    List<OutputFile> outputFiles = SplitOutputMatcher.computeBestOutput(
                            variantData.outputs,
                            variantData.variantConfiguration.getSupportedAbis(),
                            device.getDensity(), device.getAbis())

                    if (outputFiles.isEmpty()) {
                        project.logger.lifecycle(
                                "Skipping device '${device.getName()}' for '${projectName}:${variantName}': " +
                                "Could not find build of variant which supports density ${device.getDensity()} " +
                                "and an ABI in " + Joiner.on(", ").join(device.getAbis()));
                    } else {
                        List<File> apkFiles = ((List<ApkOutputFile>) outputFiles)*.getOutputFile()
                        project.logger.lifecycle("Installing APK '${Joiner.on(", ").join(apkFiles*.getName())}'" +
                                " on '${device.getName()}'")
                        if (outputFiles.size() > 1 || device.getApiLevel() >= 21) {
                            device.installPackages(apkFiles, getTimeOut(), plugin.logger);
                            successfulInstallCount++
                        } else {
                            device.installPackage(apkFiles.get(0), getTimeOut(), plugin.logger)
                            successfulInstallCount++
                        }
                    }
                } // When InstallUtils.checkDeviceApiLevel returns false, it logs the reason.
            } else {
                project.logger.lifecycle(
                        "Skipping device '${device.getName()}' for '${projectName}:${variantName}': Device not authorized, see http://developer.android.com/tools/help/adb.html#Enabling.");

            }
        }

        if (successfulInstallCount == 0) {
            if (serial != null) {
                throw new GradleException("Failed to find device with serial '${serial}'. Unset ANDROID_SERIAL to search for any device.")
            } else {
                throw new GradleException("Failed to install on any devices.")
            }
        } else {
            project.logger.quiet("Installed on ${successfulInstallCount} ${successfulInstallCount==1?'device':'devices'}.");
        }
    }
}
