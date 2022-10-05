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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.toIntExact;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.weakref.swiss.HashFunction.hash;

public class SwissGrouped
{
    private static final int GROUP_LENGTH = Long.BYTES;
    private static final VarHandle LONG_HANDLE = MethodHandles.byteArrayViewVarHandle(long[].class, LITTLE_ENDIAN);
    private static final int VALUE_WIDTH = Long.BYTES;

    private final byte[] control;
    private final byte[] values;

    private final int capacity;
    private final int mask;

    private int size;
    private final int maxSize;

    public SwissGrouped(int maxSize)
    {
        checkArgument(maxSize > 0, "maxSize must be greater than 0");
        long expandedSize = maxSize * 8L / 7L;
        expandedSize = Math.max(GROUP_LENGTH, 1L << (64 - Long.numberOfLeadingZeros(expandedSize - 1)));
        checkArgument(expandedSize < (1L << 30), "Too large (" + maxSize + " expected elements with load factor 7/8)");
        capacity = (int) expandedSize;

        this.maxSize = maxSize;
        mask = capacity - 1;
        control = new byte[capacity + GROUP_LENGTH];
        values = new byte[toIntExact(((long) VALUE_WIDTH * capacity))];
    }

    public boolean put(long value)
    {
        checkState(size < maxSize, "Table is full");

        long hash = hash(value);
        byte hashPrefix = (byte) (hash & 0x7F | 0x80);
        int bucket = bucket((int) (hash >> 7));

        while (true) {
            for (int i = bucket; i < bucket + GROUP_LENGTH; i++) {
                if (control[i] == hashPrefix) {
                    int index = bucket(i) * VALUE_WIDTH;

                    if ((long) LONG_HANDLE.get(values, index) == value) {
                        return true;
                    }
                }
            }
            for (int i = bucket; i < bucket + GROUP_LENGTH; i++) {
                if (control[i] == 0) {
                    insert(bucket(i), value, hashPrefix);
                    size++;

                    return true;
                }
            }

            bucket = bucket(bucket + GROUP_LENGTH);
        }
    }

    private void insert(int index, long value, byte hashPrefix)
    {
        control[index] = hashPrefix;
        if (index < GROUP_LENGTH) {
            control[index + capacity] = hashPrefix;
        }

        LONG_HANDLE.set(values, index * VALUE_WIDTH, value);
    }

    private int bucket(int hash)
    {
        return hash & mask;
    }
}
