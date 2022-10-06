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
import static org.weakref.swiss.HashFunction.hash;

public class SwissVector64
        implements SwissTable
{
    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_64;
    private static final VarHandle LONG_HANDLE = MethodHandles.byteArrayViewVarHandle(long[].class, LITTLE_ENDIAN);
    private static final int VALUE_WIDTH = Long.BYTES;

    private final byte[] control;
    private final byte[] values;

    private final int capacity;
    private final int mask;

    private int size;
    private final int maxSize;

    public SwissVector64(int maxSize)
    {
        checkArgument(maxSize > 0, "maxSize must be greater than 0");
        long expandedSize = maxSize * 16L / 15L;
        expandedSize = Math.max(SPECIES.length(), 1L << (64 - Long.numberOfLeadingZeros(expandedSize - 1)));
        checkArgument(expandedSize < (1L << 30), "Too large (" + maxSize + " expected elements with load factor 7/8)");
        capacity = (int) expandedSize;

        this.maxSize = maxSize;
        mask = capacity - 1;
        control = new byte[capacity + SPECIES.length()];
        values = new byte[toIntExact(((long) VALUE_WIDTH * capacity))];
    }

    public boolean put(long value)
    {
        checkState(size < maxSize, "Table is full");

        long hash = hash(value);
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
                int emptyIndex = bucket(bucket + empty);
                insert(emptyIndex, value, hashPrefix);
                size++;

                return true;
            }

            bucket = bucket(bucket + step);
            step += SPECIES.length();
        }
    }

    public boolean find(long value)
    {
        long hash = hash(value);

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

    private void insert(int bucket, long value, byte control)
    {
        this.control[bucket] = control;
        if (bucket < SPECIES.length()) {
            this.control[bucket + capacity] = control;
        }

        int index = bucket * VALUE_WIDTH;
        LONG_HANDLE.set(values, index, value);
    }

    private boolean matchInBucket(long value, byte hashPrefix, int bucket, ByteVector controlVector)
    {
        long matches = controlVector.eq(hashPrefix).toLong();
        while (matches != 0) {
            int index = bucket(bucket + Long.numberOfTrailingZeros(matches)) * VALUE_WIDTH;

            if ((long) LONG_HANDLE.get(values, index) == value) {
                return true;
            }

            matches = matches & (matches - 1);
        }
        return false;
    }

    private int bucket(int hash)
    {
        return hash & mask;
    }
}
