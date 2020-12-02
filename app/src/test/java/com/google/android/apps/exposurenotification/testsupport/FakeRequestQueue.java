/*
 * Copyright 2020 Google LLC
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
 *
 */

package com.google.android.apps.exposurenotification.testsupport;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.android.apps.exposurenotification.network.RequestQueueWrapper;
import com.google.android.apps.exposurenotification.network.RespondableByteArrayRequest;
import com.google.android.apps.exposurenotification.network.RespondableJsonObjectRequest;
import com.google.android.apps.exposurenotification.network.RespondableStringRequest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.inject.Singleton;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A fake implementation of {@link RequestQueueWrapper} that allows tests to specify responses to
 * Volley requests, both successful and failed.
 *
 * <p>Usage:
 *
 * <p>In the simple case, a test might look like this, with no fancy injection/DI issues:
 *
 * <pre>{@code
 * public class MyThingTest {
 *
 *   private FakeRequestQueue queue = new FakeRequestQueue();
 *
 *   private MyThing thingToTest;
 *
 *   @Before
 *   public void setUp() {
 *     thingToTest = new MyThing(queue);
 *   }
 *
 *   @Test
 *   public void shouldGetNetworkResource() {
 *     queue.addResponse("regex\.com/.*\.txt", 200, "some content");
 *
 *     String fileContent = thingToTest.getTextFile("http://regex.com/thing1.txt);
 *
 *     assertThat(fileContent).isEqualTo("some content");
 *   }
 *
 *   @Test
 *   public void fileNotFound_shouldThrowException() {
 *     queue.addResponse(".*", 404, "");
 *
 *     assertThrows(
 *         FileNotFoundException.class,
 *         () -> thingToTest.getTextFile("http://example.com));
 *   }
 *
 * }
 * }</pre>
 *
 * <p>When testing a SUT using RequestQueueWrapper in a deeper dependency graph, it may be
 * cleaner/safer/better to let Hilt wire things together, substituting the FakeRequestQueue wherever
 * the dependency graph calls for a RequestQueueWrapper, like so:
 *
 * <pre>{@code
 * @RunWith(RobolectricTestRunner.class)
 * @HiltAndroidTest
 * @Config(application = HiltTestApplication.class)
 * @UninstallModules(RequestQueueModule.class) // Skip the real request queue so we can use a fake.
 * public final class MyThingTest {
 *   @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);
 *   // BindValue injects the fake into Hilt's dep graph.
 *   @BindValue RequestQueueWrapper queue = new FakeRequestQueue();
 *
 *   // etc...
 *
 *   @Inject MyThing thing;
 *
 *   @Before
 *   public void setUp() {
 *       rule.inject();
 *   }
 *
 *   @Test
 *   public void testThings() {
 *     // ...
 *   }
 * }
 * }</pre>
 */
// TODO: Support headers, capture and interrogate request bodies.
@Singleton
public class FakeRequestQueue extends RequestQueueWrapper {

  private final Map<Pattern, TestResponse> responses = new HashMap<>();
  private final List<Request> requests = new ArrayList<>();

  /**
   * Dispatches the pre-configured response matching the given request.
   */
  @Override
  public <T> Request<T> add(Request<T> request) {
    requests.add(request);

    TestResponse matchingResponse = null;
    for (Map.Entry<Pattern, TestResponse> entry : responses.entrySet()) {
      if (entry.getKey().matcher(request.getUrl()).matches()) {
        matchingResponse = entry.getValue();
        // TODO: Should we remove the matching entry so that each test response is used only once,
        // or leave it so it is used for any number of matching requests?
      }
    }

    // Tests must add responses before running the SUT, which calls add(Request).
    if (matchingResponse == null) {
      throw new RuntimeException(
          "No test response matched the requested URL: [" + request.getUrl() + "]");
    }

    if (matchingResponse.httpStatus < 400) {
      // Success responses.
      if (request instanceof RespondableStringRequest) {
        ((RespondableStringRequest) request).deliverResponse(matchingResponse.responseBody);
      } else if (request instanceof RespondableByteArrayRequest) {
        ((RespondableByteArrayRequest) request)
            .deliverResponse(matchingResponse.responseBody.getBytes());
      } else if (request instanceof RespondableJsonObjectRequest) {
        // We do some awkward looking back-and-forths here to support testing how
        // RespondableJsonObjectRequest handles non-JSON responses with its own overload of
        // parseNetworkResponse(). Makes this fake request queue a bit tightly coupled to the SUT,
        // not ideal.
        NetworkResponse networkResponse =
            new NetworkResponse(matchingResponse.responseBody.getBytes());
        Response<JSONObject> jsonResponse =
            ((RespondableJsonObjectRequest) request).parseNetworkResponse(networkResponse);
        ((RespondableJsonObjectRequest) request).deliverResponse(jsonResponse.result);
      } else {
        throw new RuntimeException(FakeRequestQueue.class.getSimpleName()
            + " only works with " + RespondableStringRequest.class.getSimpleName()
            + ", " + RespondableByteArrayRequest.class.getSimpleName()
            + " and " + RespondableJsonObjectRequest.class.getSimpleName() + ".");
      }
    } else {
      // Failure responses.
      NetworkResponse response = new NetworkResponse(
          matchingResponse.httpStatus,
          matchingResponse.responseBody.getBytes(),
          /* notModified= */ true,
          /* networkTimeMs= */0L,
          /* allHeaders= */ImmutableList.of());
      request.deliverError(new VolleyError(response));
    }

    return request;
  }

  /**
   * Accepts the given response data to be dispatched to a later request added via {@link
   * #add(Request)}.
   *
   * @param uriRegex     regular expression matching the URL of the intended request.
   * @param httpStatus   status of the faked response. Statuses below 400 will cause us to invoke
   *                     the request's success listener, whereas statuses 400 and above will invoke
   *                     the error listener.
   * @param responseBody the response body, expressed as a string for convenience.
   */
  public void addResponse(String uriRegex, int httpStatus, String responseBody) {
    responses.put(Pattern.compile(uriRegex),
        new TestResponse(httpStatus, responseBody, ImmutableList.of()));
  }

  public int numRpcs() {
    return requests.size();
  }

  public List<Request> getRpcs() {
    return requests;
  }

  public Request getLastRpc() {
    return Iterables.getLast(requests);
  }

  public JSONObject getLastRpcBody() throws Exception {
    String body = new String(getLastRpc().getBody(), StandardCharsets.UTF_8);
    return new JSONObject(body);
  }

  public Map<String, String> getLastRpcHeaders() throws Exception {
    return getLastRpc().getHeaders();
  }

  /**
   * Simple value class to carry response basics.
   */
  private static class TestResponse {

    private final int httpStatus;
    private final String responseBody;
    private final List<Header> headers;

    TestResponse(int httpStatus, String responseBody, List<Header> headers) {
      this.httpStatus = httpStatus;
      this.responseBody = responseBody;
      this.headers = headers;
    }
  }

  /**
   * Simple value class to carry response headers.
   */
  private static class Header {

    private final String key;
    private final String value;

    private Header(String key, String value) {
      this.key = key;
      this.value = value;
    }
  }
}
