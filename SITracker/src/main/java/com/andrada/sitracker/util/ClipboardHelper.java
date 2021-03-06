/*
 * Copyright 2014 Gleb Godonoga.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.andrada.sitracker.util;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.Context;
import android.os.Build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class ClipboardHelper {

    @Nullable
    public static CharSequence getClipboardText(@NotNull Context context) {
        CharSequence clipboardChars;
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardChars = getClipboardHoneycomb(clipboard);
        return clipboardChars;
    }


    @Nullable
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static CharSequence getClipboardHoneycomb(@NotNull android.content.ClipboardManager clipboard) {
        if (clipboard.hasPrimaryClip()) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            return item.getText();
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    private static CharSequence getClipboardFroyo(@NotNull android.text.ClipboardManager clipboard) {
        //noinspection deprecation,AndroidLintNewApi
        return clipboard.getText();
    }
}
