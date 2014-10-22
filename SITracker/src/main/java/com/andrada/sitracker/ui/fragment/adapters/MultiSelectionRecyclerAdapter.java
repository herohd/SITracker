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

package com.andrada.sitracker.ui.fragment.adapters;

import android.support.v7.widget.RecyclerView;

public abstract class MultiSelectionRecyclerAdapter<T extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<T> {

    public abstract void toggleSelection(int pos);

    public abstract void toggleSelection(int pos, boolean value);

    public abstract void clearSelections();

    public abstract int getSelectedItemCount();

    public abstract long[] getSelectedItemsIds();

}