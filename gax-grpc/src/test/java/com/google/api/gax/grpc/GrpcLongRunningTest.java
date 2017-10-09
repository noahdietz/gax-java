/*
 * Copyright 2017, Google Inc. All rights reserved.
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
 *     * Neither the name of Google Inc. nor the names of its
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
package com.google.api.gax.grpc;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.gax.core.FakeApiClock;
import com.google.api.gax.core.RecordingScheduler;
import com.google.api.gax.grpc.testing.FakeMethodDescriptor;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.api.gax.longrunning.OperationSnapshot;
import com.google.api.gax.longrunning.OperationTimedPollAlgorithm;
import com.google.api.gax.retrying.RetrySettings;
import com.google.api.gax.rpc.CallableFactory;
import com.google.api.gax.rpc.ClientContext;
import com.google.api.gax.rpc.EmptyRequestParamsExtractor;
import com.google.api.gax.rpc.OperationCallSettings;
import com.google.api.gax.rpc.OperationCallable;
import com.google.api.gax.rpc.RequestUrlParamsEncoder;
import com.google.api.gax.rpc.SimpleCallSettings;
import com.google.api.gax.rpc.TransportChannel;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.longrunning.Operation;
import com.google.longrunning.OperationsSettings;
import com.google.longrunning.stub.GrpcOperationsStub;
import com.google.longrunning.stub.OperationsStub;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.type.Color;
import com.google.type.Money;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.Status.Code;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class GrpcLongRunningTest {
  private CallableFactory callableFactory =
      CallableFactory.create(GrpcTransportDescriptor.create());

  private static final RetrySettings FAST_RETRY_SETTINGS =
      RetrySettings.newBuilder()
          .setInitialRetryDelay(Duration.ofMillis(1L))
          .setRetryDelayMultiplier(1)
          .setMaxRetryDelay(Duration.ofMillis(1L))
          .setInitialRpcTimeout(Duration.ofMillis(1L))
          .setMaxAttempts(0)
          .setJittered(false)
          .setRpcTimeoutMultiplier(1)
          .setMaxRpcTimeout(Duration.ofMillis(1L))
          .setTotalTimeout(Duration.ofMillis(5L))
          .build();

  private ManagedChannel initialChannel;
  private ManagedChannel pollChannel;
  private GrpcLongRunningClient longRunningClient;
  private RecordingScheduler executor;
  private ClientContext initialContext;
  private OperationCallSettings<Integer, Color, Money> callSettings;

  private FakeApiClock clock;
  private OperationTimedPollAlgorithm pollingAlgorithm;

  @Before
  public void setUp() throws IOException {
    initialChannel = mock(ManagedChannel.class);
    pollChannel = mock(ManagedChannel.class);
    TransportChannelProvider operationsChannelProvider = mock(TransportChannelProvider.class);
    TransportChannel transportChannel =
        GrpcTransportChannel.newBuilder().setManagedChannel(pollChannel).build();
    when(operationsChannelProvider.getTransportChannel()).thenReturn(transportChannel);

    clock = new FakeApiClock(0L);
    executor = RecordingScheduler.create(clock);
    pollingAlgorithm = OperationTimedPollAlgorithm.create(FAST_RETRY_SETTINGS, clock);

    OperationsSettings.Builder settingsBuilder = OperationsSettings.newBuilder();
    settingsBuilder
        .getOperationSettings()
        .setRetrySettings(FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(1).build());
    OperationsSettings settings =
        OperationsSettings.newBuilder()
            .setTransportChannelProvider(operationsChannelProvider)
            .build();
    OperationsStub operationsStub = GrpcOperationsStub.create(settings);
    longRunningClient = GrpcLongRunningClient.create(operationsStub);

    SimpleCallSettings<Integer, OperationSnapshot> initialCallSettings =
        SimpleCallSettings.<Integer, OperationSnapshot>newBuilder()
            .setRetrySettings(FAST_RETRY_SETTINGS.toBuilder().setMaxAttempts(1).build())
            .build();

    callSettings =
        OperationCallSettings.<Integer, Color, Money>newBuilder()
            .setInitialCallSettings(initialCallSettings)
            .setResponseTransformer(GrpcOperationTransformers.ResponseTransformer.of(Color.class))
            .setMetadataTransformer(GrpcOperationTransformers.MetadataTransformer.of(Money.class))
            .setPollingAlgorithm(pollingAlgorithm)
            .build();

    initialContext = getClientContext(initialChannel, executor);
  }

  @Test
  public void testCall() {
    Color resp = getColor(1.0f);
    Money meta = getMoney("UAH");
    Operation resultOperation = getOperation("testCall", resp, meta, true);
    mockResponse(initialChannel, Code.OK, resultOperation);

    OperationCallable<Integer, Color, Money> callable =
        callableFactory.create(
            GrpcOperationTransformers.StartOperationCallable.of(createDirectCallable()),
            callSettings,
            initialContext,
            longRunningClient);

    Color response = callable.call(2, GrpcCallContext.createDefault());
    assertThat(response).isEqualTo(resp);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  @Test
  public void testFutureCallPollDoneOnFirst() throws Exception {
    String opName = "testFutureCallPollDoneOnFirst";
    Color resp = getColor(0.5f);
    Money meta = getMoney("UAH");
    Operation initialOperation = getOperation(opName, null, null, false);
    Operation resultOperation = getOperation(opName, resp, meta, true);
    mockResponse(initialChannel, Code.OK, initialOperation);
    mockResponse(pollChannel, Code.OK, resultOperation);

    OperationCallable<Integer, Color, Money> callable =
        callableFactory.create(
            GrpcOperationTransformers.StartOperationCallable.of(createDirectCallable()),
            callSettings,
            initialContext,
            longRunningClient);

    OperationFuture<Color, Money> future = callable.futureCall(2, GrpcCallContext.createDefault());

    assertFutureSuccessMetaSuccess(opName, future, resp, meta);
    assertThat(executor.getIterationsCount()).isEqualTo(0);
  }

  private void assertFutureSuccessMetaSuccess(
      String opName, OperationFuture<Color, Money> future, Color resp, Money meta)
      throws Exception {
    assertThat(future.getName()).isEqualTo(opName);
    assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo(resp);
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCancelled()).isFalse();
    assertThat(future.get()).isEqualTo(resp);

    assertThat(future.peekMetadata().get()).isEqualTo(meta);
    assertThat(future.peekMetadata()).isSameAs(future.peekMetadata());
    assertThat(future.peekMetadata().isDone()).isTrue();
    assertThat(future.peekMetadata().isCancelled()).isFalse();

    assertThat(future.getMetadata().get()).isEqualTo(meta);
    assertThat(future.getMetadata()).isSameAs(future.getMetadata());
    assertThat(future.getMetadata().isDone()).isTrue();
    assertThat(future.getMetadata().isCancelled()).isFalse();
  }

  private Color getColor(float blueValue) {
    return Color.newBuilder().setBlue(blueValue).build();
  }

  private Money getMoney(String currencyCode) {
    return Money.newBuilder().setCurrencyCode(currencyCode).build();
  }

  private ClientContext getClientContext(
      ManagedChannel channel, ScheduledExecutorService executor) {
    return ClientContext.newBuilder()
        .setTransportChannel(GrpcTransportChannel.newBuilder().setManagedChannel(channel).build())
        .setExecutor(executor)
        .build();
  }

  private Operation getOperation(String name, Message response, Message metadata, boolean done) {
    Operation.Builder builder = Operation.newBuilder().setName(name).setDone(done);
    if (response instanceof com.google.rpc.Status) {
      builder.setError((com.google.rpc.Status) response);
    } else if (response != null) {
      builder.setResponse(Any.pack(response));
    }
    if (metadata != null) {
      builder.setMetadata(Any.pack(metadata));
    }
    return builder.build();
  }

  @SuppressWarnings("unchecked")
  private void mockResponse(ManagedChannel channel, Code statusCode, Object... results) {
    Status status = statusCode.toStatus();
    ClientCall<Integer, ?> clientCall = new MockClientCall<>(results[0], status);
    ClientCall<Integer, ?>[] moreCalls = new ClientCall[results.length - 1];
    for (int i = 0; i < results.length - 1; i++) {
      moreCalls[i] = new MockClientCall<>(results[i + 1], status);
    }
    when(channel.newCall(any(MethodDescriptor.class), any(CallOptions.class)))
        .thenReturn(clientCall, moreCalls);
  }

  private UnaryCallable<Integer, Operation> createDirectCallable() {
    return new GrpcDirectCallable<Integer, Operation>(
        FakeMethodDescriptor.<Integer, Operation>create(),
        new RequestUrlParamsEncoder<>(EmptyRequestParamsExtractor.<Integer>of(), false));
  }
}
