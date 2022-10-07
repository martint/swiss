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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.toIntExact;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.weakref.swiss.Common.DEFAULT_LOAD_FACTOR;
import static org.weakref.swiss.Common.computeCapacity;
import static org.weakref.swiss.Common.hash;

public class SwissPseudoVector
        implements SwissTable
{
    private static final int VECTOR_LENGTH = Long.BYTES;
    private static final VarHandle LONG_HANDLE = MethodHandles.byteArrayViewVarHandle(long[].class, LITTLE_ENDIAN);

    private final byte[] control;
    private final byte[] values;

    private final int capacity;
    private final int mask;

    private int size;
    private final int maxSize;
    private final int entrySize;

    public SwissPseudoVector(int entrySize, int maxSize, double loadFactor)
    {
        checkArgument(entrySize > 0, "entrySize must be greater than 0");
        checkArgument(maxSize > 0, "maxSize must be greater than 0");
        checkArgument(loadFactor > 0 && loadFactor <= 1, "loadFactor must be in (0, 1] range");
        int capacity = Math.max(VECTOR_LENGTH, computeCapacity(maxSize, loadFactor));

        this.entrySize = entrySize;
        this.maxSize = maxSize;
        this.capacity = capacity;
        mask = capacity - 1;
        control = new byte[capacity + VECTOR_LENGTH];
        values = new byte[toIntExact(((long) entrySize * capacity))];
    }

    public SwissPseudoVector(int entrySize, int maxSize)
    {
        this(entrySize, maxSize, DEFAULT_LOAD_FACTOR);
    }

    public boolean put(long hash, byte[] value)
    {
        checkArgument(value.length == entrySize);

        byte hashPrefix = (byte) (hash & 0x7F | 0x80);
        int bucket = bucket((int) (hash >> 7));

        int step = 1;
        long repeated = repeat(hashPrefix);

        while (true) {
            final long controlVector = (long) LONG_HANDLE.get(control, bucket);

            if (matchInBucket(value, bucket, repeated, controlVector)) {
                return false;
            }

            int empty = findEmpty(controlVector);
            if (empty != VECTOR_LENGTH) {
                checkState(size < maxSize, "Table is full");

                int emptyIndex = bucket(bucket + empty);
                insert(emptyIndex, value, hashPrefix);

                size++;
                
                return true;
            }

            bucket = bucket(bucket + step);
            step += VECTOR_LENGTH;
        }
    }

    public boolean find(long hash, byte[] value)
    {
        checkArgument(value.length == entrySize);

        byte hashPrefix = (byte) (hash & 0x7F | 0x80);
        int bucket = bucket((int) (hash >> 7));

        int step = 1;
        long repeated = repeat(hashPrefix);

        while (true) {
            final long controlVector = (long) LONG_HANDLE.get(control, bucket);

            if (matchInBucket(value, bucket, repeated, controlVector)) {
                return true;
            }

            if (findEmpty(controlVector) != VECTOR_LENGTH) {
                return false;
            }

            bucket = bucket(bucket + step);
            step += VECTOR_LENGTH;
        }
    }

    @Override
    public void clear()
    {
        size = 0;
        Arrays.fill(control, (byte) 0);
    }

    private int findEmpty(long vector)
    {
        long controlMatches = match(vector, 0x00_00_00_00_00_00_00_00L);
        return controlMatches != 0 ? (Long.numberOfTrailingZeros(controlMatches) >>> 3) : VECTOR_LENGTH;
    }

    private boolean matchInBucket(byte[] value, int bucket, long repeated, long controlVector)
    {
        long controlMatches = match(controlVector, repeated);
        while (controlMatches != 0) {

            if (valueEquals(bucket(bucket + (Long.numberOfTrailingZeros(controlMatches) >>> 3)), value)) {
                return true;
            }

            controlMatches = controlMatches & (controlMatches - 1);
        }
        return false;
    }

    private void insert(int index, byte[] value, byte hashPrefix)
    {
        control[index] = hashPrefix;
        if (index < VECTOR_LENGTH) {
            control[index + capacity] = hashPrefix;
        }

        System.arraycopy(value, 0, values, index * entrySize, value.length);
    }

    private static long repeat(byte value)
    {
        return ((value & 0xFF) * 0x01_01_01_01_01_01_01_01L);
    }

    private static long match(long vector, long repeatedValue)
    {
        // HD 6-1
        long comparison = vector ^ repeatedValue;
        return (comparison - 0x01_01_01_01_01_01_01_01L) & ~comparison & 0x80_80_80_80_80_80_80_80L;
    }

    private int bucket(int hash)
    {
        return hash & mask;
    }

    private boolean valueEquals(int bucket, byte[] value)
    {
        int start = bucket * entrySize;
        return Arrays.equals(values, start, start + value.length, value, 0, value.length);
    }
}
