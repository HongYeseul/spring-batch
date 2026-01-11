/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.batch.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

/**
 * Tests for StepContext collision issue when using StepScopeTestUtils with
 * StepScopeTestExecutionListener active.
 *
 * @see <a href="https://github.com/spring-projects/spring-batch/issues/5181">GitHub Issue
 * #5181</a>
 * @author Spring Batch Community
 */
@SpringJUnitConfig
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class, StepScopeTestExecutionListener.class })
class StepScopeContextCollisionTests {

	/**
	 * This method provides the default StepExecution for StepScopeTestExecutionListener.
	 * It uses the default MetaDataInstanceFactory values which can cause collision.
	 */
	StepExecution getStepExecution() {
		return MetaDataInstanceFactory.createStepExecution();
	}

	/**
	 * Test that StepScopeTestUtils.doInStepScope() can register a new context with
	 * different JobParameters even when StepScopeTestExecutionListener has already
	 * registered a context with default values.
	 *
	 * This test reproduces the issue described in GitHub #5181 where
	 * MetaDataInstanceFactory's default values cause StepContext collision.
	 */
	@Test
	void shouldNotCollideWhenUsingStepScopeTestUtilsWithDifferentJobParameters() throws Exception {
		String expectedValue = "testValue";
		JobParameters jobParameters = new JobParametersBuilder().addString("testParam", expectedValue)
			.toJobParameters();

		// Create a StepExecution with custom JobParameters
		// Before the fix, this would have the same ID as the one registered by the
		// listener
		StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution(jobParameters);

		StepScopeTestUtils.doInStepScope(stepExecution, () -> {
			StepContext context = StepSynchronizationManager.getContext();
			assertNotNull(context, "StepContext should not be null");

			// Verify that we're getting the correct JobParameters
			// Before the fix, this would return null because the listener's context
			// (which has no JobParameters) would be retrieved instead
			String actualValue = (String) context.getJobParameters().get("testParam");
			assertEquals(expectedValue, actualValue,
					"JobParameters should be accessible from the StepContext registered by StepScopeTestUtils");

			return null;
		});
	}

	/**
	 * Test that multiple calls to createStepExecution with different JobParameters create
	 * distinct StepExecution instances that don't collide.
	 */
	@Test
	void multipleStepExecutionsWithDifferentJobParametersShouldNotCollide() throws Exception {
		JobParameters params1 = new JobParametersBuilder().addString("param", "value1").toJobParameters();
		JobParameters params2 = new JobParametersBuilder().addString("param", "value2").toJobParameters();

		StepExecution step1 = MetaDataInstanceFactory.createStepExecution(params1);
		StepExecution step2 = MetaDataInstanceFactory.createStepExecution(params2);

		// Register first StepExecution
		StepScopeTestUtils.doInStepScope(step1, () -> {
			StepContext context = StepSynchronizationManager.getContext();
			assertEquals("value1", context.getJobParameters().get("param"));
			return null;
		});

		// Register second StepExecution - should not collide with the first
		StepScopeTestUtils.doInStepScope(step2, () -> {
			StepContext context = StepSynchronizationManager.getContext();
			assertEquals("value2", context.getJobParameters().get("param"));
			return null;
		});
	}

}
