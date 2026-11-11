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

package org.springframework.web.servlet.function;

import java.io.File;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.handler.PathPatternsTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * @author Arjen Poutsma
 */
class PathResourceLookupFunctionTests {

	@Test
	void normal() throws Exception {
		ClassPathResource location = new ClassPathResource("org/springframework/web/servlet/function/");
		PathResourceLookupFunction function = new PathResourceLookupFunction("/resources/**", location);
		ServerRequest request = initRequest("GET", "/resources/response.txt");

		Optional<Resource> result = function.apply(request);
		assertThat(result).isPresent();

		File expected = new ClassPathResource("response.txt", getClass()).getFile();
		assertThat(result.get().getFile()).isEqualTo(expected);
	}

	@Test
	void subPath() throws Exception {
		ClassPathResource location = new ClassPathResource("org/springframework/web/servlet/function/");
		PathResourceLookupFunction function = new PathResourceLookupFunction("/resources/**", location);
		ServerRequest request = initRequest("GET", "/resources/child/response.txt");

		Optional<Resource> result = function.apply(request);
		assertThat(result).isPresent();

		File expected = new ClassPathResource("org/springframework/web/servlet/function/child/response.txt").getFile();
		assertThat(result.get().getFile()).isEqualTo(expected);
	}

	@Test
	void notFound() {
		ClassPathResource location = new ClassPathResource("org/springframework/web/reactive/function/server/");
		PathResourceLookupFunction function = new PathResourceLookupFunction("/resources/**", location);
		ServerRequest request = initRequest("GET", "/resources/foo.txt");

		Optional<Resource> result = function.apply(request);
		assertThat(result).isNotPresent();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"%252E%252E/testsecret/secret.txt",
			"%252e%252e%252ftestsecret%252fsecret.txt",
			"..%252F..%252Ftestsecret/secret.txt",
			"%2E%2E%252F%2E%2E%252Ftestsecret/secret.txt",
			"child/%252E%252E/%252E%252E/testsecret/secret.txt"
	})
	void doubleEncodedPathTraversalIsRejected(String path) throws Exception {
		// Use mock location: i.e. we're only testing upfront validations...
		Resource location = mock();
		given(location.createRelative(any())).willThrow(new AssertionError("Resource should not be resolved"));

		PathResourceLookupFunction function = new PathResourceLookupFunction("/resources/**", location);
		ServerRequest request = initRequest("GET", "/resources/" + path);

		Optional<Resource> result = function.apply(request);
		assertThat(result).isNotPresent();
	}

	@Test
	void doubleEncodedPathIsNormalizedWithinLocation() throws Exception {
		ClassPathResource location = new ClassPathResource("org/springframework/web/servlet/function/");
		PathResourceLookupFunction function = new PathResourceLookupFunction("/resources/**", location);
		ServerRequest request = initRequest("GET", "/resources/child/%252E%252E/response.txt");

		Optional<Resource> result = function.apply(request);
		assertThat(result).isPresent();

		File expected = new ClassPathResource("response.txt", getClass()).getFile();
		assertThat(result.get().getFile()).isEqualTo(expected);
	}

	@Test
	void composeResourceLookupFunction() throws Exception {
		ClassPathResource defaultResource = new ClassPathResource("response.txt", getClass());

		Function<ServerRequest, Optional<Resource>> lookupFunction =
				new PathResourceLookupFunction("/resources/**",
						new ClassPathResource("org/springframework/web/servlet/function/"));

		Function<ServerRequest, Optional<Resource>> customLookupFunction =
				lookupFunction.andThen((Optional<Resource> optionalResource) -> {
					if (optionalResource.isPresent()) {
						return optionalResource;
					}
					else {
						return Optional.of(defaultResource);
					}
				});

		ServerRequest request = initRequest("GET", "/resources/foo");

		Optional<Resource> result = customLookupFunction.apply(request);
		assertThat(result).isPresent();

		assertThat(result.get().getFile()).isEqualTo(defaultResource.getFile());
	}

	private ServerRequest initRequest(String httpMethod, String requestUri) {
		return new DefaultServerRequest(
				PathPatternsTestUtils.initRequest(httpMethod, requestUri, true),
				Collections.emptyList());
	}

}
