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

package com.android.manifmerger;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces all placeholders of the form ${name} with a tool invocation provided value
 */
public class PlaceholderHandler {

    // interesting placeholders names that are documented to be automatically provided.
    public static final String INSTRUMENTATION_RUNNER = "instrumentationRunner";
    public static final String PACKAGE_NAME = "packageName";
    public static final String APPLICATION_ID = "applicationId";

    // regular expression to recognize placeholders like ${name}, potentially surrounded by a
    // prefix and suffix string. this will split in 3 groups, the prefix, the placeholder name, and
    // the suffix.
    private static final Pattern PATTERN = Pattern.compile("([^\\$]*)\\$\\{([^\\}]*)\\}(.*)");

    /**
     * Interface to provide a value for a placeholder key.
     * @param <T> the key type
     */
    public interface KeyBasedValueResolver<T> {

        /**
         * Returns a placeholder value for the placeholder key or null if none exists.
         */
        @Nullable
        String getValue(@NonNull T key);
    }

    /**
     * Returns true if the passed string is a placeholder value, false otherwise.
     */
    public static boolean isPlaceHolder(@NonNull String string) {
        return PATTERN.matcher(string).matches();
    }

    /**
     * Visits a document's entire tree and check each attribute for a placeholder existence.
     * If one is found, delegate to the provided {@link KeyBasedValueResolver} to provide a value
     * for the placeholder.
     * <p>
     * If no value is provided, an error will be generated.
     *
     * @param xmlDocument the xml document to visit
     * @param valueProvider the placeholder value provider.
     * @param mergingReportBuilder to report errors and log actions.
     */
    public void visit(
            @NonNull ManifestMerger2.MergeType mergeType,
            @NonNull XmlDocument xmlDocument,
            @NonNull KeyBasedValueResolver<String> valueProvider,
            @NonNull MergingReport.Builder mergingReportBuilder) {

        visit(mergeType, xmlDocument.getRootNode(), valueProvider, mergingReportBuilder);
    }

    private void visit(
            @NonNull ManifestMerger2.MergeType mergeType,
            @NonNull XmlElement xmlElement,
            @NonNull KeyBasedValueResolver<String> valueProvider,
            @NonNull MergingReport.Builder mergingReportBuilder) {

        for (XmlAttribute xmlAttribute : xmlElement.getAttributes()) {

            Matcher matcher = PATTERN.matcher(xmlAttribute.getValue());
            if (matcher.matches()) {
                String placeholderValue = valueProvider.getValue(matcher.group(2));
                if (placeholderValue == null) {
                    // if this is a library, ignore the failure
                    MergingReport.Record.Severity severity =
                            mergeType == ManifestMerger2.MergeType.LIBRARY
                        // revert to INFO once no placeholder substitution is lib is optional.
                        ? MergingReport.Record.Severity.ERROR
                        : MergingReport.Record.Severity.ERROR;

                    xmlAttribute.addMessage(mergingReportBuilder, severity,
                            String.format(
                                    "Attribute %1$s at %2$s requires a placeholder substitution"
                                            + " but no value for <%3$s> is provided.",
                                    xmlAttribute.getId(),
                                    xmlAttribute.printPosition(),
                                    matcher.group(2)
                            ));
                } else {
                    // record the attribute set
                    mergingReportBuilder.getActionRecorder().recordAttributeAction(
                            xmlAttribute,
                            PositionImpl.UNKNOWN,
                            Actions.ActionType.INJECTED,
                            null /* attributeOperationType */);

                    String attrValue = matcher.group(1) + placeholderValue + matcher.group(3);
                    xmlAttribute.getXml().setValue(attrValue);
                }
            }
        }
        for (XmlElement childElement : xmlElement.getMergeableElements()) {
            visit(mergeType, childElement, valueProvider, mergingReportBuilder);
        }
    }
}
