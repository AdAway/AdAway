/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.support.v4.os;

import android.os.Parcel;

/**
 * Callbacks a {@link Parcelable} creator should implement.
 */
public interface ParcelableCompatCreatorCallbacks<T> {

    /**
     * Create a new instance of the Parcelable class, instantiating it
     * from the given Parcel whose data had previously been written by
     * {@link Parcelable#writeToParcel Parcelable.writeToParcel()} and
     * using the given ClassLoader.
     *
     * @param in The Parcel to read the object's data from.
     * @param loader The ClassLoader that this object is being created in.
     * @return Returns a new instance of the Parcelable class.
     */
    public T createFromParcel(Parcel in, ClassLoader loader);

    /**
     * Create a new array of the Parcelable class.
     *
     * @param size Size of the array.
     * @return Returns an array of the Parcelable class, with every entry
     *         initialized to null.
     */
    public T[] newArray(int size);
}
