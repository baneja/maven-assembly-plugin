/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.assembly.format;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.maven.plugins.assembly.AssemblerConfigurationSource;
import org.apache.maven.plugins.assembly.utils.AssemblyFileUtils;
import org.apache.maven.plugins.assembly.utils.LineEndings;
import org.apache.maven.plugins.assembly.utils.LineEndingsUtils;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenReaderFilterRequest;
import org.codehaus.plexus.components.io.functions.InputStreamTransformer;
import org.codehaus.plexus.components.io.resources.PlexusIoResource;

/**
 *
 */
public class ReaderFormatter {
    private static Reader createReaderFilter(
            Reader source,
            String escapeString,
            List<String> delimiters,
            AssemblerConfigurationSource configSource,
            boolean isPropertiesFile,
            Properties additionalProperties)
            throws IOException {
        try {

            MavenReaderFilterRequest filterRequest = new MavenReaderFilterRequest(
                    source,
                    true,
                    configSource.getProject(),
                    configSource.getFilters(),
                    isPropertiesFile,
                    configSource.getMavenSession(),
                    additionalProperties);

            filterRequest.setEscapeString(escapeString);

            // if these are NOT set, just use the defaults, which are '${*}' and '@'.
            if (delimiters != null && !delimiters.isEmpty()) {
                LinkedHashSet<String> delims = new LinkedHashSet<>();
                for (String delim : delimiters) {
                    if (delim == null) {
                        // FIXME: ${filter:*} could also trigger this condition. Need a better long-term solution.
                        delims.add("${*}");
                    } else {
                        delims.add(delim);
                    }
                }

                filterRequest.setDelimiters(delims);
            } else {
                filterRequest.setDelimiters(filterRequest.getDelimiters());
            }

            filterRequest.setInjectProjectBuildFilters(configSource.isIncludeProjectBuildFilters());
            return configSource.getMavenReaderFilter().filter(filterRequest);
        } catch (MavenFilteringException e) {
            IOException ioe = new IOException("Error filtering file '" + source + "': " + e.getMessage(), e);
            throw ioe;
        }
    }

    private static boolean isForbiddenFiletypes(PlexusIoResource plexusIoResource) {
        String fileName = plexusIoResource.getName().toLowerCase();
        return (fileName.endsWith(".zip") || fileName.endsWith(".jar"));
    }

    /* private static void checkifFileTypeIsAppropriateForLineEndingTransformation(PlexusIoResource plexusIoResource)
            throws IOException {
        if (isForbiddenFiletypes(plexusIoResource)) {
            throw new IOException("Cannot transform line endings on this kind of file: " + plexusIoResource.getName()
                    + "\nDoing so is more or less guaranteed to destroy the file, and it indicates"
                    + " a problem with your assembly descriptor."
                    + "\nThis error message is new as of 2.5.3. "
                    + "\nEarlier versions of assembly-plugin will silently destroy your file. "
                    + "Fix your descriptor");
        }
    } */

    // refactored code with better method name

    private static void validateFileType(PlexusIoResource plexusIoResource) throws IOException {
        if (isForbiddenFiletypes(plexusIoResource)) {
            throw new IOException("Cannot transform line endings on this kind of file: " + plexusIoResource.getName()
                    + "\nDoing so is more or less guaranteed to destroy the file, and it indicates"
                    + " a problem with your assembly descriptor."
                    + "\nThis error message is new as of 2.5.3. "
                    + "\nEarlier versions of assembly-plugin will silently destroy your file. "
                    + "Fix your descriptor");
        }
    }

    public static InputStreamTransformer getFileSetTransformers(
            final AssemblerConfigurationSource configSource,
            final boolean isFiltered,
            final Set<String> nonFilteredFileExtensions,
            String fileSetLineEnding)
            throws AssemblyFormattingException {
        final LineEndings lineEndingToUse = LineEndingsUtils.getLineEnding(fileSetLineEnding);

        final boolean transformLineEndings = !LineEndings.keep.equals(lineEndingToUse);

        if (transformLineEndings || isFiltered) {
            return new InputStreamTransformer() {
                @Override
                public InputStream transform(PlexusIoResource plexusIoResource, InputStream inputStream)
                        throws IOException {
                    final String fileName = plexusIoResource.getName();
                    for (String extension : nonFilteredFileExtensions) {
                        if (fileName.endsWith('.' + extension)) {
                            return inputStream;
                        }
                    }

                    InputStream result = inputStream;
                    if (isFiltered) {
                        boolean isPropertyFile = AssemblyFileUtils.isPropertyFile(plexusIoResource.getName());
                        final String encoding = isPropertyFile ? "ISO-8859-1" : configSource.getEncoding();

                        Reader source = encoding != null
                                ? new InputStreamReader(inputStream, encoding)
                                : new InputStreamReader(inputStream); // wtf platform encoding ? TODO: Fix this
                        Reader filtered = createReaderFilter(
                                source,
                                configSource.getEscapeString(),
                                configSource.getDelimiters(),
                                configSource,
                                isPropertyFile,
                                configSource.getAdditionalProperties());
                        result = encoding != null
                                ? new ReaderInputStream(filtered, encoding)
                                : new ReaderInputStream(filtered);
                    }
                    if (transformLineEndings) {
                        validateFileType(plexusIoResource); // updated method name from above
                        result = LineEndingsUtils.lineEndingConverter(result, lineEndingToUse);
                    }
                    return result;
                }
            };
        }
        return null;
    }
}
