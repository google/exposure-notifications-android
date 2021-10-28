/*
 * Copyright 2021 Google LLC
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

package com.google.android.apps.exposurenotification.slices;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.slice.Slice.Builder;
import android.content.Context;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.drawable.IconCompat;
import androidx.slice.Slice;
import androidx.slice.SliceConvert;
import androidx.slice.builders.ListBuilder;
import androidx.slice.builders.ListBuilder.RowBuilder;
import androidx.slice.builders.SliceAction;
import com.google.android.apps.exposurenotification.common.logging.Logger;
import com.google.common.collect.ImmutableList;

@RequiresApi(VERSION_CODES.KITKAT)
public abstract class ListSliceBuilderWrapper {
  private static final Logger logger = Logger.getLogger("ListSliceBuilderWrapper");
  private int size = 0;

  /** Creates a {@link ListSliceBuilderWrapper}. */
  public static ListSliceBuilderWrapper createListSliceBuilderWrapper(
      Context context, Uri sliceUri) {
    ListBuilder listBuilder;
    try {
      listBuilder = new ListBuilder(context, sliceUri, ListBuilder.INFINITY);
    } catch (IllegalStateException e) {
      logger.w("Meet exception when constructing ListBuilder!");
      listBuilder = null;
    }

    if (listBuilder != null) {
      return new AndroidXListSliceBuilder(listBuilder);
    } else if (VERSION.SDK_INT >= VERSION_CODES.P) {
      return new NativeListSliceBuilder(context, sliceUri);
    } else {
      logger.w("Build androidx Slice fail and os version is " +
          VERSION.SDK_INT);
      // Return an empty builder for this case.
      return new EmptyListSliceBuilderWrapper();
    }
  }

  /** Adds a row in the SliceBuilder. */
  public void addRow(PendingIntent action, IconCompat icon, String title, String subtitle) {
    size++;
  }

  /** Returns true if at least one row was added */
  public boolean isEmpty() {
    return size == 0;
  }

  /** Builds {@link Slice} from builder. */
  public abstract Slice build();

  /** Uses {@link androidx.slice.builders.ListBuilder} to build list slice. */
  @VisibleForTesting
  static class AndroidXListSliceBuilder extends ListSliceBuilderWrapper {
    private final ListBuilder listBuilder;

    private AndroidXListSliceBuilder(ListBuilder listBuilder) {
      this.listBuilder = listBuilder;
      logger.i("Build with AndroidXListSliceBuilder");
    }

    @Override
    public void addRow(PendingIntent action, IconCompat iconCompat, String title, String subtitle) {
      super.addRow(action, iconCompat, title, subtitle);
      listBuilder.addRow(
          new RowBuilder()
              .setTitleItem(iconCompat, ListBuilder.ICON_IMAGE)
              .setTitle(title)
              .setSubtitle(subtitle)
              .setPrimaryAction(
                  SliceAction.createDeeplink(action, iconCompat, ListBuilder.ICON_IMAGE, title)));
    }

    @Override
    public Slice build() {
      return listBuilder.build();
    }
  }

  /** Uses {@link android.app.slice.Slice.Builder} to build list slice. */
  @VisibleForTesting
  @SuppressLint("NewApi")
  @RequiresApi(VERSION_CODES.P)
  static class NativeListSliceBuilder extends ListSliceBuilderWrapper {
    private static final ImmutableList<String> HINT_TITLE =
        ImmutableList.of(android.app.slice.Slice.HINT_TITLE);
    private static final ImmutableList<String> HINT_LIST_ITEM =
        ImmutableList.of(android.app.slice.Slice.HINT_LIST_ITEM);
    private static final ImmutableList<String> HINT_SHORTCUT =
        ImmutableList.of(android.app.slice.Slice.HINT_TITLE, android.app.slice.Slice.HINT_SHORTCUT);
    private static final ImmutableList<String> HINT_EMPTY = ImmutableList.of();

    private final Context context;
    private final Builder builder;

    private NativeListSliceBuilder(Context context, Uri sliceUri) {
      this.context = context;
      this.builder = new Builder(sliceUri, /* spec= */ null);
      logger.i("Build with NativeListSliceBuilder");
    }

    @Override
    public void addRow(PendingIntent action, IconCompat iconCompat, String title, String subtitle) {
      super.addRow(action, iconCompat, title, subtitle);
      builder.addSubSlice(
          new Builder(builder)
              .addSubSlice(
                  new Builder(builder)
                      .addAction(
                          action,
                          new Builder(builder)
                              .addIcon(iconCompat.toIcon(context), /* subType= */ null, HINT_EMPTY)
                              .build(),
                          /* subType= */ null)
                      .addHints(HINT_SHORTCUT)
                      .build(),
                  /* subType= */ null)
              .addHints(HINT_LIST_ITEM)
              .addText(title, /* subType= */ null, HINT_TITLE)
              .addText(subtitle, /* subType= */ null, HINT_EMPTY)
              .build(),
          /* subType= */ null);
    }

    @Override
    public Slice build() {
      return SliceConvert.wrap(builder.build(), context);
    }
  }

  /**
   * Empty builder which will be used if above builders are not working. Won't do anything but only
   * return an empty slice when {@link #build} called.
   */
  static class EmptyListSliceBuilderWrapper extends ListSliceBuilderWrapper {
    private EmptyListSliceBuilderWrapper() {
      logger.i("Build with EmptyListSliceBuilderWrapper");
    }

    @Override
    public void addRow(PendingIntent action, IconCompat icon, String title, String subtitle) {
      super.addRow(action, icon, title, subtitle);
    }

    @Nullable
    @Override
    public Slice build() {
      return null;
    }
  }
}
