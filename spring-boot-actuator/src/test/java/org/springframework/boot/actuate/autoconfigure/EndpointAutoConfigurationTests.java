/*
 * Copyright 2012-2016 the original author or authors.
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

package org.springframework.boot.actuate.autoconfigure;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import liquibase.integration.spring.SpringLiquibase;
import org.flywaydb.core.Flyway;
import org.junit.After;
import org.junit.Test;

import org.springframework.boot.actuate.endpoint.AutoConfigurationReportEndpoint;
import org.springframework.boot.actuate.endpoint.BeansEndpoint;
import org.springframework.boot.actuate.endpoint.DumpEndpoint;
import org.springframework.boot.actuate.endpoint.EnvironmentEndpoint;
import org.springframework.boot.actuate.endpoint.FlywayEndpoint;
import org.springframework.boot.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.actuate.endpoint.InfoEndpoint;
import org.springframework.boot.actuate.endpoint.LiquibaseEndpoint;
import org.springframework.boot.actuate.endpoint.MetricsEndpoint;
import org.springframework.boot.actuate.endpoint.PublicMetrics;
import org.springframework.boot.actuate.endpoint.RequestMappingEndpoint;
import org.springframework.boot.actuate.endpoint.ShutdownEndpoint;
import org.springframework.boot.actuate.endpoint.TraceEndpoint;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.EmbeddedDataSourceConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.test.EnvironmentTestUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link EndpointAutoConfiguration}.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Greg Turnquist
 * @author Christian Dupuis
 * @author Stephane Nicoll
 * @author Eddú Meléndez
 */
public class EndpointAutoConfigurationTests {

	private AnnotationConfigApplicationContext context;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void endpoints() throws Exception {
		load(EndpointAutoConfiguration.class);
		assertNotNull(this.context.getBean(BeansEndpoint.class));
		assertNotNull(this.context.getBean(DumpEndpoint.class));
		assertNotNull(this.context.getBean(EnvironmentEndpoint.class));
		assertNotNull(this.context.getBean(HealthEndpoint.class));
		assertNotNull(this.context.getBean(InfoEndpoint.class));
		assertNotNull(this.context.getBean(MetricsEndpoint.class));
		assertNotNull(this.context.getBean(ShutdownEndpoint.class));
		assertNotNull(this.context.getBean(TraceEndpoint.class));
		assertNotNull(this.context.getBean(RequestMappingEndpoint.class));
	}

	@Test
	public void healthEndpoint() {
		load(EmbeddedDataSourceConfiguration.class, EndpointAutoConfiguration.class,
				HealthIndicatorAutoConfiguration.class);
		HealthEndpoint bean = this.context.getBean(HealthEndpoint.class);
		assertNotNull(bean);
		Health result = bean.invoke();
		assertNotNull(result);
		assertTrue("Wrong result: " + result, result.getDetails().containsKey("db"));
	}

	@Test
	public void healthEndpointWithDefaultHealthIndicator() {
		load(EndpointAutoConfiguration.class, HealthIndicatorAutoConfiguration.class);
		HealthEndpoint bean = this.context.getBean(HealthEndpoint.class);
		assertNotNull(bean);
		Health result = bean.invoke();
		assertNotNull(result);
	}

	@Test
	public void metricEndpointsHasSystemMetricsByDefault() {
		load(PublicMetricsAutoConfiguration.class, EndpointAutoConfiguration.class);
		MetricsEndpoint endpoint = this.context.getBean(MetricsEndpoint.class);
		Map<String, Object> metrics = endpoint.invoke();
		assertTrue(metrics.containsKey("mem"));
		assertTrue(metrics.containsKey("heap.used"));
	}

	@Test
	public void metricEndpointCustomPublicMetrics() {
		load(CustomPublicMetricsConfig.class, PublicMetricsAutoConfiguration.class,
				EndpointAutoConfiguration.class);
		MetricsEndpoint endpoint = this.context.getBean(MetricsEndpoint.class);
		Map<String, Object> metrics = endpoint.invoke();

		// Custom metrics
		assertTrue(metrics.containsKey("foo"));

		// System metrics still available
		assertTrue(metrics.containsKey("mem"));
		assertTrue(metrics.containsKey("heap.used"));

	}

	@Test
	public void autoConfigurationAuditEndpoints() {
		load(EndpointAutoConfiguration.class, ConditionEvaluationReport.class);
		assertNotNull(this.context.getBean(AutoConfigurationReportEndpoint.class));
	}

	@Test
	public void testInfoEndpointConfiguration() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		EnvironmentTestUtils.addEnvironment(this.context, "info.foo:bar");
		this.context.register(EndpointAutoConfiguration.class);
		this.context.refresh();
		InfoEndpoint endpoint = this.context.getBean(InfoEndpoint.class);
		assertNotNull(endpoint);
		assertNotNull(endpoint.invoke().get("git"));
		assertEquals("bar", endpoint.invoke().get("foo"));
	}

	@Test
	public void testNoGitProperties() throws Exception {
		this.context = new AnnotationConfigApplicationContext();
		EnvironmentTestUtils.addEnvironment(this.context,
				"spring.git.properties:classpath:nonexistent");
		this.context.register(EndpointAutoConfiguration.class);
		this.context.refresh();
		InfoEndpoint endpoint = this.context.getBean(InfoEndpoint.class);
		assertNotNull(endpoint);
		assertNull(endpoint.invoke().get("git"));
	}

	@Test
	public void testFlywayEndpoint() {
		this.context = new AnnotationConfigApplicationContext();
		this.context.register(EmbeddedDataSourceConfiguration.class,
				FlywayAutoConfiguration.class, EndpointAutoConfiguration.class);
		this.context.refresh();
		FlywayEndpoint endpoint = this.context.getBean(FlywayEndpoint.class);
		assertNotNull(endpoint);
		assertEquals(1, endpoint.invoke().size());
	}

	@Test
	public void flywayEndpointIsDisabledWhenThereAreMultipleFlywayBeans() {
		this.context = new AnnotationConfigApplicationContext();
		this.context.register(MultipleFlywayBeansConfig.class,
				EndpointAutoConfiguration.class);
		this.context.refresh();
		assertThat(this.context.getBeansOfType(FlywayEndpoint.class).size(),
				is(equalTo(0)));
	}

	@Test
	public void testLiquibaseEndpoint() {
		this.context = new AnnotationConfigApplicationContext();
		this.context.register(EmbeddedDataSourceConfiguration.class,
				LiquibaseAutoConfiguration.class, EndpointAutoConfiguration.class);
		this.context.refresh();
		LiquibaseEndpoint endpoint = this.context.getBean(LiquibaseEndpoint.class);
		assertNotNull(endpoint);
		assertEquals(1, endpoint.invoke().size());
	}

	@Test
	public void liquibaseEndpointIsDisabledWhenThereAreMultipleSpringLiquibaseBeans() {
		this.context = new AnnotationConfigApplicationContext();
		this.context.register(MultipleLiquibaseBeansConfig.class,
				EndpointAutoConfiguration.class);
		this.context.refresh();
		assertThat(this.context.getBeansOfType(LiquibaseEndpoint.class).size(),
				is(equalTo(0)));
	}

	private void load(Class<?>... config) {
		this.context = new AnnotationConfigApplicationContext();
		this.context.register(config);
		this.context.refresh();
	}

	@Configuration
	static class CustomPublicMetricsConfig {

		@Bean
		PublicMetrics customPublicMetrics() {
			return new PublicMetrics() {
				@Override
				public Collection<Metric<?>> metrics() {
					Metric<Integer> metric = new Metric<Integer>("foo", 1);
					return Collections.<Metric<?>>singleton(metric);
				}
			};
		}

	}

	@Configuration
	static class MultipleFlywayBeansConfig {

		@Bean
		Flyway flywayOne() {
			return mock(Flyway.class);
		}

		@Bean
		Flyway flywayTwo() {
			return mock(Flyway.class);
		}

	}

	@Configuration
	static class MultipleLiquibaseBeansConfig {

		@Bean
		SpringLiquibase liquibaseOne() {
			return mock(SpringLiquibase.class);
		}

		@Bean
		SpringLiquibase liquibaseTwo() {
			return mock(SpringLiquibase.class);
		}

	}

}