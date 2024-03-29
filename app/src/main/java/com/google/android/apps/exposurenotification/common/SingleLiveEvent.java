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

package com.google.android.apps.exposurenotification.common;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A lifecycle-aware observable that sends only new updates after subscription, used for events like
 * navigation and Snackbar messages.
 *
 * <p>This avoids a common problem with events: on configuration change (like rotation) an update
 * can be emitted if the observer is active. This LiveData only calls the observable if there's an
 * explicit call to setValue() or call().
 *
 * <p>Note that only one observer is going to be notified of changes.
 */
public class SingleLiveEvent<T> extends MutableLiveData<T> {

  private static final Logger logger = Logger.getLogger("SingleLiveEvent");

  private final AtomicBoolean mPending = new AtomicBoolean(false);

  @MainThread
  @Override
  public void observe(@NonNull LifecycleOwner owner, @NonNull final Observer<? super T> observer) {
    if (hasActiveObservers()) {
      logger.w("Multiple observers registered but only one will be notified of changes.");
    }

    // Observe the internal MutableLiveData
    super.observe(
        owner,
        t -> {
          if (mPending.compareAndSet(true, false)) {
            observer.onChanged(t);
          }
        });
  }

  @MainThread
  @Override
  public void setValue(@Nullable T t) {
    mPending.set(true);
    super.setValue(t);
  }

  /** Used for cases where T is Void, to make calls cleaner. */
  @MainThread
  public void call() {
    setValue(null);
  }

  @Override
  public void postValue(@Nullable T t) {
    mPending.set(true);
    super.postValue(t);
  }

  /** Used for cases where T is Void, to make post calls cleaner. */
  public void postCall() {
    postValue(null);
  }
}
