/*
 * Copyright 2002-2010 the original author or authors.
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

package org.springframework.core.env;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockPropertySource;


public class PropertyResolverTests {
	private Properties testProperties;
	private PropertySources propertySources;
	private ConfigurablePropertyResolver propertyResolver;

	@Before
	public void setUp() {
		propertySources = new PropertySources();
		propertyResolver = new PropertySourcesPropertyResolver(propertySources);
		testProperties = new Properties();
		propertySources.addFirst(new PropertiesPropertySource("testProperties", testProperties));
	}

	@Test
	public void containsProperty() {
		assertThat(propertyResolver.containsProperty("foo"), is(false));
		testProperties.put("foo", "bar");
		assertThat(propertyResolver.containsProperty("foo"), is(true));
	}

	@Test
	public void getProperty() {
		assertThat(propertyResolver.getProperty("foo"), nullValue());
		testProperties.put("foo", "bar");
		assertThat(propertyResolver.getProperty("foo"), is("bar"));
	}

	@Test
	public void getProperty_propertySourceSearchOrderIsFIFO() {
		PropertySources sources = new PropertySources();
		PropertyResolver resolver = new PropertySourcesPropertyResolver(sources);
		sources.addFirst(new MockPropertySource("ps1").withProperty("pName", "ps1Value"));
		assertThat(resolver.getProperty("pName"), equalTo("ps1Value"));
		sources.addFirst(new MockPropertySource("ps2").withProperty("pName", "ps2Value"));
		assertThat(resolver.getProperty("pName"), equalTo("ps2Value"));
		sources.addFirst(new MockPropertySource("ps3").withProperty("pName", "ps3Value"));
		assertThat(resolver.getProperty("pName"), equalTo("ps3Value"));
	}

	@Test
	public void getProperty_withExplicitNullValue() {
		// java.util.Properties does not allow null values (because Hashtable does not)
		Map<String, String> nullableProperties = new HashMap<String, String>();
		propertySources.addLast(new MapPropertySource("nullableProperties", nullableProperties));
		nullableProperties.put("foo", null);
		assertThat(propertyResolver.getProperty("foo"), nullValue());
	}

	@Test
	public void getProperty_withStringArrayConversion() {
		testProperties.put("foo", "bar,baz");
		assertThat(propertyResolver.getProperty("foo", String[].class), equalTo(new String[] { "bar", "baz" }));
	}


	@Test
	public void getProperty_withNonConvertibleTargetType() {
		testProperties.put("foo", "bar");

		class TestType { }

		try {
			propertyResolver.getProperty("foo", TestType.class);
			fail("Expected IllegalArgumentException due to non-convertible types");
		} catch (IllegalArgumentException ex) {
			// expected
		}
	}

	@Test
	public void getProperty_doesNotCache_replaceExistingKeyPostConstruction() {
		String key = "foo";
		String value1 = "bar";
		String value2 = "biz";

		HashMap<String, String> map = new HashMap<String, String>();
		map.put(key, value1); // before construction
		PropertySources propertySources = new PropertySources();
		propertySources.addFirst(new MapPropertySource("testProperties", map));
		PropertyResolver propertyResolver = new PropertySourcesPropertyResolver(propertySources);
		assertThat(propertyResolver.getProperty(key), equalTo(value1));
		map.put(key, value2); // after construction and first resolution
		assertThat(propertyResolver.getProperty(key), equalTo(value2));
	}

	@Test
	public void getProperty_doesNotCache_addNewKeyPostConstruction() {
		HashMap<String, String> map = new HashMap<String, String>();
		PropertySources propertySources = new PropertySources();
		propertySources.addFirst(new MapPropertySource("testProperties", map));
		PropertyResolver propertyResolver = new PropertySourcesPropertyResolver(propertySources);
		assertThat(propertyResolver.getProperty("foo"), equalTo(null));
		map.put("foo", "42");
		assertThat(propertyResolver.getProperty("foo"), equalTo("42"));
	}

	@Test
	public void getPropertySources_replacePropertySource() {
		propertySources = new PropertySources();
		propertyResolver = new PropertySourcesPropertyResolver(propertySources);
		propertySources.addLast(new MockPropertySource("local").withProperty("foo", "localValue"));
		propertySources.addLast(new MockPropertySource("system").withProperty("foo", "systemValue"));

		// 'local' was added first so has precedence
		assertThat(propertyResolver.getProperty("foo"), equalTo("localValue"));

		// replace 'local' with new property source
		propertySources.replace("local", new MockPropertySource("new").withProperty("foo", "newValue"));

		// 'system' now has precedence
		assertThat(propertyResolver.getProperty("foo"), equalTo("newValue"));

		assertThat(propertySources.size(), is(2));
	}

	@Test
	public void getRequiredProperty() {
		testProperties.put("exists", "xyz");
		assertThat(propertyResolver.getRequiredProperty("exists"), is("xyz"));

		try {
			propertyResolver.getRequiredProperty("bogus");
			fail("expected IllegalArgumentException");
		} catch (IllegalArgumentException ex) {
			// expected
		}
	}

	@Test
	public void getRequiredProperty_withStringArrayConversion() {
		testProperties.put("exists", "abc,123");
		assertThat(propertyResolver.getRequiredProperty("exists", String[].class), equalTo(new String[] { "abc", "123" }));

		try {
			propertyResolver.getRequiredProperty("bogus", String[].class);
			fail("expected IllegalArgumentException");
		} catch (IllegalArgumentException ex) {
			// expected
		}
	}

	@Test
	public void asProperties() {
		propertySources = new PropertySources();
		propertyResolver = new PropertySourcesPropertyResolver(propertySources);
		assertThat(propertyResolver.asProperties(), notNullValue());

		propertySources.addLast(new MockPropertySource("highestPrecedence").withProperty("common", "highCommon").withProperty("highKey", "highVal"));
		propertySources.addLast(new MockPropertySource("middlePrecedence").withProperty("common", "midCommon").withProperty("midKey", "midVal"));
		propertySources.addLast(new MockPropertySource("lowestPrecedence").withProperty("common", "lowCommon").withProperty("lowKey", "lowVal"));

		Properties props = propertyResolver.asProperties();
		assertThat(props.getProperty("common"), is("highCommon"));
		assertThat(props.getProperty("lowKey"), is("lowVal"));
		assertThat(props.getProperty("midKey"), is("midVal"));
		assertThat(props.getProperty("highKey"), is("highVal"));
		assertThat(props.size(), is(4));
	}

	@Test
	public void resolvePlaceholders() {
		testProperties.setProperty("foo", "bar");
		String resolved = propertyResolver.resolvePlaceholders("pre-${foo}-${unresolvable}-post");
		assertThat(resolved, is("pre-bar-${unresolvable}-post"));
	}

	@Test
	public void resolveRequiredPlaceholders() {
		testProperties.setProperty("foo", "bar");
		try {
			propertyResolver.resolveRequiredPlaceholders("pre-${foo}-${unresolvable}-post");
			fail("expected exception");
		} catch (IllegalArgumentException ex) {
			assertThat(ex.getMessage(), is("Could not resolve placeholder 'unresolvable'"));
		}
	}

}
