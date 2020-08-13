/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.randomcutforest.store;

import static com.amazon.randomcutforest.CommonUtils.checkArgument;
import static com.amazon.randomcutforest.CommonUtils.checkState;

import java.util.Arrays;

/**
 * PointStore is a fixed size repository of points, where each point is a double
 * array of a specified length. A PointStore counts references to points that
 * are added, and frees space internally when a given point is no longer in use.
 * The primary use of this store is to enable compression since the points in
 * two different trees do not have to be stored separately.
 *
 * Stored points are referenced by index values which can be used to look up the
 * point values and increment and decrement reference counts. Valid index values
 * are between 0 (inclusive) and capacity (exclusive).
 */
public class PointStore {

    private final double[] store;
    private final int[] refCount;
    private final int[] freeBlockStack;
    private int freeBlockPointer;
    private final int dimensions;
    private final int capacity;

    /**
     * Create a new PointStore with the given dimensions and capacity.
     *
     * @param dimensions The number of dimensions in stored points.
     * @param capacity   The maximum number of points that can be stored.
     */
    public PointStore(int dimensions, int capacity) {
        checkArgument(dimensions > 0, "dimensions must be greater than 0");
        checkArgument(capacity > 0, "capacity must be greater than 0");

        this.capacity = capacity;
        this.dimensions = dimensions;
        store = new double[capacity * dimensions];
        refCount = new int[capacity];
        freeBlockStack = new int[capacity];

        for (int j = 0; j < capacity; j++) {
            freeBlockStack[j] = capacity - j - 1; // reverse order
        }

        freeBlockPointer = capacity - 1;
    }

    /**
     * @return the number of dimensions in stored points for this PointStore.
     */
    public int getDimensions() {
        return dimensions;
    }

    /**
     * @return the maximum number of points that can be stored.
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * @param index The index value.
     * @return the reference count for the given index. The value 0 indicates that
     *         there is no point stored at that index.
     */
    public int getRefCount(int index) {
        return refCount[index];
    }

    /**
     * @return the number of points currently stored in this point store.
     */
    public int size() {
        return capacity - freeBlockPointer - 1;
    }

    /**
     * Add a point to the point store and return the index of the stored point.
     *
     * @param point The point being added to the store.
     * @return the index value of the stored point.
     * @throws IllegalArgumentException if the length of the point does not match
     *                                  the point store's dimensions.
     * @throws IllegalStateException    if the point store is full.
     */
    public int add(double[] point) {
        checkArgument(point.length == dimensions, "point.length must be equal to dimensions");
        checkState(freeBlockPointer >= 0, "point store is full");

        int nextIndex = freeBlockStack[freeBlockPointer--];
        System.arraycopy(point, 0, store, nextIndex * dimensions, dimensions);

        refCount[nextIndex] = 1;
        return nextIndex;
    }

    /**
     * Increment the reference count for the given index. This operation assumes
     * that there is currently a point stored at the given index and will throw an
     * exception if that's not the case.
     *
     * @param index The index value.
     * @throws IllegalArgumentException if the index value is not valid.
     * @throws IllegalArgumentException if the current reference count for this
     *                                  index is nonpositive.
     */
    public void incrementRefCount(int index) {
        checkValidIndex(index);
        refCount[index]++;
    }

    /**
     * Decrement the reference count for the given index.
     *
     * @param index The index value.
     * @throws IllegalArgumentException if the index value is not valid.
     * @throws IllegalArgumentException if the current reference count for this
     *                                  index is nonpositive.
     */
    public void decrementRefCount(int index) {
        checkValidIndex(index);

        // TODO these should be unreachable states and we should remove these checks in
        // a future revision
        checkState(size() > 0, "tried to decrement reference count while size is nonpositive");
        checkState(freeBlockPointer > -2, String.format("invalid value in free block index: %d", freeBlockPointer));

        refCount[index]--;
        if (refCount[index] == 0) {
            freeBlockStack[++freeBlockPointer] = index;
        }
    }

    /**
     * Test whether the given point is equal to the point stored at the given index.
     * This operation uses pointwise <code>==</code> to test for equality.
     *
     * @param index The index value of the point we are comparing to.
     * @param point The point we are comparing for equality.
     * @return true if the point stored at the index is equal to the given point,
     *         false otherwise.
     * @throws IllegalArgumentException if the index value is not valid.
     * @throws IllegalArgumentException if the current reference count for this
     *                                  index is nonpositive.
     * @throws IllegalArgumentException if the length of the point does not match
     *                                  the point store's dimensions.
     */
    public boolean pointEquals(int index, double[] point) {
        checkValidIndex(index);
        checkArgument(point.length == dimensions, "point.length must be equal to dimensions");

        for (int j = 0; j < dimensions; j++) {
            if (point[j] != store[j + index * dimensions]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get a copy of the point at the given index.
     * 
     * @param index An index value corresponding to a storage location in this point
     *              store.
     * @return a copy of the point stored at the given index.
     * @throws IllegalArgumentException if the index value is not valid.
     * @throws IllegalArgumentException if the current reference count for this
     *                                  index is nonpositive.
     */
    public double[] get(int index) {
        checkValidIndex(index);
        return Arrays.copyOfRange(store, index * dimensions, (index + 1) * dimensions);
    }

    private void checkValidIndex(int index) {
        checkArgument(index >= 0 && index < capacity, "index must be between 0 (inclusive) and capacity (exclusive)");
        checkArgument(refCount[index] > 0, String.format("index value %d is not in use", index));
    }
}