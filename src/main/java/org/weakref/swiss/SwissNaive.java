/*
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
package org.weakref.swiss;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.toIntExact;
import static org.weakref.swiss.Common.DEFAULT_LOAD_FACTOR;
import static org.weakref.swiss.Common.computeCapacity;

public class SwissNaive
        implements SwissTable
{
    private final byte[] control;
    private final byte[] values;

    private final int mask;

    private int size;
    private final int maxSize;
    private final int entrySize;

    public SwissNaive(int entrySize, int maxSize, double loadFactor)
    {
        checkArgument(entrySize > 0, "entrySize must be greater than 0");
        checkArgument(maxSize > 0, "maxSize must be greater than 0");
        checkArgument(loadFactor > 0 && loadFactor <= 1, "loadFactor must be in (0, 1] range");
        int capacity = computeCapacity(maxSize, loadFactor);

        this.entrySize = entrySize;
        this.maxSize = maxSize;
        mask = capacity - 1;
        control = new byte[capacity];
        values = new byte[toIntExact(((long) entrySize * capacity))];
    }

    public SwissNaive(int entrySize, int maxSize)
    {
        this(entrySize, maxSize, DEFAULT_LOAD_FACTOR);
    }

    public boolean put(long hash, byte[] value)
    {
        checkArgument(value.length == entrySize);

        byte hashPrefix = (byte) (hash & 0x7F | 0x80);
        int bucket = bucket((int) (hash >> 7));

        int step = 1;
        while (true) {
            byte controlEntry = control[bucket];
            if (controlEntry == hashPrefix && valueEquals(bucket, value)) {
                return false;
            }
            
            if (controlEntry == 0) {
                checkState(size < maxSize, "Table is full");

                insert(bucket, value, hashPrefix);
                size++;

                return true;
            }

            bucket = bucket(bucket + step);
            step++;
        }
    }

    public boolean find(long hash, byte[] value)
    {
        checkArgument(value.length == entrySize);

        byte hashPrefix = (byte) (hash & 0x7F | 0x80);
        int bucket = bucket((int) (hash >> 7));

        int step = 1;
        while (true) {
            byte controlEntry = control[bucket];
            if (controlEntry == hashPrefix && valueEquals(bucket, value)) {
                return true;
            }

            if (controlEntry == 0) {
                return false;
            }

            bucket = bucket(bucket + step);
            step++;
        }
    }

    @Override
    public void clear()
    {
        size = 0;
        Arrays.fill(control, (byte) 0);
    }

    private void insert(int index, byte[] value, byte hashPrefix)
    {
        control[index] = hashPrefix;
        System.arraycopy(value, 0, values, index * entrySize, value.length);
    }

    private boolean valueEquals(int bucket, byte[] value)
    {
        int start = bucket * entrySize;
        return Arrays.equals(values, start, start + value.length, value, 0, value.length);
    }

    private int bucket(int hash)
    {
        return hash & mask;
    }
}
