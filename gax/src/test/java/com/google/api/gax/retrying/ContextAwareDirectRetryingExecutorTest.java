/*
 * Copyright 2021 Google LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google LLC nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.google.api.gax.retrying;

import static com.google.api.gax.retrying.FailingCallable.FAILING_RETRY_SETTINGS;
import static com.google.api.gax.retrying.FailingCallable.FAST_RETRY_SETTINGS;

import com.google.api.core.CurrentMillisClock;
import com.google.api.gax.rpc.testing.FakeCallContext;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ContextAwareDirectRetryingExecutorTest extends AbstractRetryingExecutorTest {

  @Override
  @Before
  public void setUp() {
    retryingContext =
        FakeCallContext.createDefault().withTracer(tracer).withRetrySettings(FAST_RETRY_SETTINGS);
  }

  @Override
  protected RetryingExecutorWithContext<String> getExecutor(RetryAlgorithm<String> retryAlgorithm) {
    return new ContextAwareDirectRetryingExecutor<>(
        (ContextAwareRetryAlgorithm<String>) retryAlgorithm);
  }

  @Override
  protected ContextAwareRetryAlgorithm<String> getAlgorithm(
      RetrySettings retrySettings, int apocalypseCountDown, RuntimeException apocalypseException) {
    return new ContextAwareRetryAlgorithm<>(
        new TestResultRetryAlgorithm<String>(apocalypseCountDown, apocalypseException),
        new ExponentialRetryAlgorithm(retrySettings, CurrentMillisClock.getDefaultClock()));
  }

  protected RetrySettings getDefaultRetrySettings() {
    return FAILING_RETRY_SETTINGS;
  }
}
