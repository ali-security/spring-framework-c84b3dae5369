/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.reactive.resource;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * Resource handling utility methods to share common logic between
 * {@link ResourceWebHandler} and other resource handling code.
 *
 * @author Rossen Stoyanchev
 * @since 6.0.12
 */
public abstract class ResourceHandlerUtils {

	/**
	 * Normalize the given resource path replacing the following:
	 * <ul>
	 * <li>Backslash with forward slash.
	 * <li>Duplicate occurrences of slash with a single slash.
	 * <li>Any combination of leading slash and control characters (00-1F and 7F)
	 * with a single "/" or "".
	 * <li>Percent-encoded sequences decoded, and if the decoded path contains
	 * "../" it is cleaned via {@link StringUtils#cleanPath}.
	 * </ul>
	 * @param path the path to normalize
	 * @return the normalized path
	 */
	public static String normalizeInputPath(String path) {
		path = StringUtils.replace(path, "\\", "/");
		path = cleanDuplicateSlashes(path);
		path = cleanLeadingSlash(path);
		return normalizePath(path);
	}

	private static String cleanDuplicateSlashes(String path) {
		StringBuilder sb = null;
		char prev = 0;
		for (int i = 0; i < path.length(); i++) {
			char curr = path.charAt(i);
			try {
				if (curr == '/' && prev == '/') {
					if (sb == null) {
						sb = new StringBuilder(path.substring(0, i));
					}
					continue;
				}
				if (sb != null) {
					sb.append(path.charAt(i));
				}
			}
			finally {
				prev = curr;
			}
		}
		return (sb != null ? sb.toString() : path);
	}

	private static String cleanLeadingSlash(String path) {
		boolean slash = false;
		for (int i = 0; i < path.length(); i++) {
			if (path.charAt(i) == '/') {
				slash = true;
			}
			else if (path.charAt(i) > ' ' && path.charAt(i) != 127) {
				if (i == 0 || (i == 1 && slash)) {
					return path;
				}
				return (slash ? "/" + path.substring(i) : path.substring(i));
			}
		}
		return (slash ? "/" : "");
	}

	private static String normalizePath(String path) {
		if (path.contains("%")) {
			try {
				path = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
			}
			catch (Exception ex) {
				return "";
			}
			if (path.contains("../")) {
				path = StringUtils.cleanPath(path);
			}
		}
		return path;
	}

	/**
	 * Whether the given input path is invalid as determined by this method.
	 * <p>The default implementation rejects paths that:
	 * <ul>
	 * <li>Contain "WEB-INF" or "META-INF"
	 * <li>Contain "://" after processing (potential URL injection)
	 * <li>Contain "../" after cleaning
	 * </ul>
	 * @param path the path to check
	 * @return {@code true} if the path should be rejected, {@code false} otherwise
	 */
	public static boolean shouldIgnoreInputPath(String path) {
		if (path.contains("WEB-INF") || path.contains("META-INF")) {
			return true;
		}
		if (path.contains(":/")) {
			String relativePath = (path.charAt(0) == '/' ? path.substring(1) : path);
			if (ResourceUtils.isUrl(relativePath) || relativePath.startsWith("url:")) {
				return true;
			}
		}
		if (path.contains("../")) {
			return true;
		}
		return false;
	}

	/**
	 * Check whether the given resource is under the given location.
	 * @param location the location to check against
	 * @param resource the resource to check
	 * @return {@code true} if the resource is under the location
	 * @throws IOException if an I/O error occurs
	 */
	public static boolean isResourceUnderLocation(Resource location, Resource resource) throws IOException {
		if (resource.getClass() != location.getClass()) {
			return false;
		}

		String resourcePath;
		String locationPath;

		if (resource instanceof UrlResource) {
			resourcePath = resource.getURL().toExternalForm();
			locationPath = StringUtils.cleanPath(location.getURL().toString());
		}
		else if (resource instanceof ClassPathResource classPathResource) {
			resourcePath = classPathResource.getPath();
			locationPath = StringUtils.cleanPath(((ClassPathResource) location).getPath());
		}
		else {
			resourcePath = resource.getURL().getPath();
			locationPath = StringUtils.cleanPath(location.getURL().getPath());
		}

		if (locationPath.equals(resourcePath)) {
			return true;
		}
		locationPath = (locationPath.endsWith("/") || locationPath.isEmpty() ? locationPath : locationPath + "/");
		return resourcePath.startsWith(locationPath);
	}

}
