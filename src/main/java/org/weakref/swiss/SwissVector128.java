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

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorSpecies;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.toIntExact;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.weakref.swiss.Common.DEFAULT_LOAD_FACTOR;
import static org.weakref.swiss.Common.computeCapacity;

public class SwissVector128
        implements SwissTable
{
    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_128;
    private static final VarHandle LONG_HANDLE = MethodHandles.byteArrayViewVarHandle(long[].class, LITTLE_ENDIAN);

    private final byte[] control;
    private final byte[] values;

    private final int capacity;
    private final int mask;

    private int size;
    private final int maxSize;
    private final int entrySize;

    public SwissVector128(int entrySize, int maxSize, double loadFactor)
    {
        checkArgument(entrySize > 0, "entrySize must be greater than 0");
        checkArgument(maxSize > 0, "maxSize must be greater than 0");
        checkArgument(loadFactor > 0 && loadFactor <= 1, "loadFactor must be in (0, 1] range");
        int capacity = Math.max(SPECIES.length(), computeCapacity(maxSize, loadFactor));

        this.entrySize = entrySize;
        this.maxSize = maxSize;
        this.capacity = capacity;
        mask = capacity - 1;
        control = new byte[capacity + SPECIES.length()];
        values = new byte[toIntExact(((long) entrySize * capacity))];
    }

    public SwissVector128(int entrySize, int maxSize)
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
            ByteVector controlVector = ByteVector.fromArray(SPECIES, control, bucket);

            if (matchInBucket(value, hashPrefix, bucket, controlVector)) {
                return false;
            }

            int empty = findEmpty(controlVector);
            if (empty != SPECIES.length()) {
                checkState(size < maxSize, "Table is full");

                int emptyIndex = bucket(bucket + empty);
                insert(emptyIndex, value, hashPrefix);
                size++;

                return true;
            }

            bucket = bucket(bucket + step);
            step += SPECIES.length();
        }
    }

    public boolean find(long hash, byte[] value)
    {
        byte hashPrefix = (byte) (hash & 0x7F | 0x80);
        int bucket = bucket((int) (hash >> 7));

        int step = 1;
        while (true) {
            ByteVector controlVector = ByteVector.fromArray(SPECIES, control, bucket);

            if (matchInBucket(value, hashPrefix, bucket, controlVector)) {
                return true;
            }

            if (findEmpty(controlVector) != SPECIES.length()) {
                return false;
            }

            bucket = bucket(bucket + step);
            step += SPECIES.length();
        }
    }

    @Override
    public void clear()
    {
        size = 0;
        Arrays.fill(control, (byte) 0);
    }

    private static int findEmpty(ByteVector vector)
    {
        return vector.eq((byte) 0).firstTrue();
    }

    private void insert(int bucket, byte[] value, byte control)
    {
        this.control[bucket] = control;
        if (bucket < SPECIES.length()) {
            this.control[bucket + capacity] = control;
        }

        System.arraycopy(value, 0, values, bucket * entrySize, value.length);
    }

    private boolean matchInBucket(byte[] value, byte hashPrefix, int bucket, ByteVector controlVector)
    {
        long matches = controlVector.eq(hashPrefix).toLong();
        while (matches != 0) {
            if (valueEquals(bucket(bucket + Long.numberOfTrailingZeros(matches)), value)) {
                return true;
            }

            matches = matches & (matches - 1);
        }
        return false;
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
