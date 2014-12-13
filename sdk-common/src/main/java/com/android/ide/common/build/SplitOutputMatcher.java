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

package com.android.ide.common.build;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import com.android.resources.Density;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class to help with installation of multi-output variants.
 */
public class SplitOutputMatcher {

    /**
     * Determines and return the list of APKs to use based on given device density and abis.
     *
     * This uses the same logic as the store, using two passes:
     * First, find all the compatible outputs.
     * Then take the one with the highest versionCode.
     *
     * @param outputs the outputs to choose from.
     * @param variantAbiFilters a list of abi filters applied to the variant. This is used in place
     *                          of the outputs, if there is a single output with no abi filters.
     *                          If the list is null, then the variant does not restrict ABI
     *                          packaging.
     * @param deviceDensity the density of the device.
     * @param deviceAbis a list of ABIs supported by the device.
     * @return the list of APKs to install or null if none are compatible.
     */
    @NonNull
    public static List<OutputFile> computeBestOutput(
            @NonNull List<? extends VariantOutput> outputs,
            @Nullable Set<String> variantAbiFilters,
            int deviceDensity,
            @NonNull List<String> deviceAbis) {
        Density densityEnum = Density.getEnum(deviceDensity);

        String densityValue;
        if (densityEnum == null) {
            densityValue = null;
        } else {
            densityValue = densityEnum.getResourceValue();
        }

        // gather all compatible matches.
        Set<VariantOutput> matches = new HashSet<VariantOutput>();

        // find a matching output.
        for (VariantOutput variantOutput : outputs) {
            for (OutputFile output : variantOutput.getOutputs()) {
                String densityFilter = getFilter(output, OutputFile.DENSITY);
                String abiFilter = getFilter(output, OutputFile.ABI);

                if (densityFilter != null && !densityFilter.equals(densityValue)) {
                    continue;
                }

                if (abiFilter != null && !deviceAbis.contains(abiFilter)) {
                    continue;
                }
                // variantOutput can be added several times to matches.
                matches.add(variantOutput);
            }
        }

        if (matches.isEmpty()) {
            return ImmutableList.of();
        }

        VariantOutput match = Collections.max(matches, new Comparator<VariantOutput>() {
            @Override
            public int compare(VariantOutput splitOutput, VariantOutput splitOutput2) {
                return splitOutput.getVersionCode() - splitOutput2.getVersionCode();
            }
        });

        OutputFile mainOutputFile = match.getMainOutputFile();
        if (match.getOutputs().size() == 1) {
            return isMainApkCompatibleWithDevice(mainOutputFile, variantAbiFilters, deviceAbis)
                    ? ImmutableList.<OutputFile>of(mainOutputFile)
                    : ImmutableList.<OutputFile>of();
        } else {
            // we are dealing with pure splits.
            ImmutableList.Builder<OutputFile> apks = ImmutableList.builder();
            apks.add(mainOutputFile);
            Optional<OutputFile> abiCompatibleSplitApk = findAbiCompatibleSplitApk(match,
                    deviceAbis);
            if (abiCompatibleSplitApk.isPresent()) {
                apks.add(abiCompatibleSplitApk.get());
            }
            Optional<OutputFile> densityCompatibleSplitApk = findDensityCompatibleSplitApk(match,
                    densityValue);
            if (densityCompatibleSplitApk.isPresent()) {
                apks.add(densityCompatibleSplitApk.get());
            }
            return apks.build();
        }
    }

    private static boolean isMainApkCompatibleWithDevice(
            OutputFile mainOutputFile,
            Set<String> variantAbiFilters,
            List<String> deviceAbis) {
        // so far, we are not dealing with the pure split files...
        if (getFilter(mainOutputFile, OutputFile.ABI) == null && variantAbiFilters != null) {
            // if we have a match that has no abi filter, and we have variant-level filters, then
            // we need to make sure that the variant filters are compatible with the device abis.
            for (String abi : deviceAbis) {
                if (variantAbiFilters.contains(abi)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private static Optional<OutputFile> findAbiCompatibleSplitApk(
            VariantOutput variantOutput,
            List<String> deviceAbis) {

        for (String deviceAbi : deviceAbis) {
            for (OutputFile outputFile : variantOutput.getOutputs()) {
                if (outputFile.getOutputType().equals(OutputFile.SPLIT)
                        && deviceAbi.equals(getFilter(outputFile, OutputFile.ABI))) {
                    return Optional.of(outputFile);
                }
            }
        }
        return Optional.absent();
    }

    private static Optional<OutputFile> findDensityCompatibleSplitApk(
            VariantOutput variantOutput,
            String densityValue) {
        for (OutputFile outputFile : variantOutput.getOutputs()) {
            if (outputFile.getOutputType().equals(OutputFile.SPLIT)
                    && densityValue.equals(getFilter(outputFile, OutputFile.DENSITY))) {
                return Optional.of(outputFile);
            }
        }
        return Optional.absent();
    }

    @Nullable
    private static String getFilter(@NonNull OutputFile outputFile, @NonNull String filterType) {
        for (FilterData filterData : outputFile.getFilters()) {
            if (filterData.getFilterType().equals(filterType)) {
                return filterData.getIdentifier();
            }
        }
        return null;
    }
}
