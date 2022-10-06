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
package org.weakref.swiss.benchmark;

import com.google.common.primitives.Longs;
import it.unimi.dsi.fastutil.bytes.ByteArrays;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.RunnerException;
import org.weakref.swiss.SwissNaive;
import org.weakref.swiss.SwissPseudoVector;
import org.weakref.swiss.SwissVector128;
import org.weakref.swiss.SwissVector64;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.weakref.swiss.Common.DEFAULT_LOAD_FACTOR;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgsAppend = {
        "--enable-preview",
        "--add-modules=jdk.incubator.vector",
        "-XX:+UnlockDiagnosticVMOptions",
//        "-XX:CompileCommand=print,*.*",
//        "-XX:+PrintAssembly",
        "-XX:PrintAssemblyOptions=intel"
})
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
@OperationsPerInvocation(BenchmarkFind.OPERATIONS)
public class BenchmarkFind
{
    public final static int OPERATIONS = 10_000;

    @Param({"8", "100"})
    public int payloadSize = 1000;

    @Param({"1000", "1000000"})
    public int size = 1000;

    @Param({"0.1", "0.9", "1"})
    public double fillFraction = 0.1;

    @Param({"0", "0.5", "1"})
    public double findMissingFraction = 1; // what percentage of find() operations will look for a missing item

    private byte[][] values;

    private SwissVector128 vector128;
    private SwissVector64 vector64;
    private SwissPseudoVector pseudoVector;
    private SwissNaive naive;
    private ObjectOpenCustomHashSet<byte[]> fastutil;

    @Setup
    public void setup()
    {
        vector128 = new SwissVector128(payloadSize, size);
        vector64 = new SwissVector64(payloadSize, size);
        pseudoVector = new SwissPseudoVector(payloadSize, size);
        naive = new SwissNaive(payloadSize, size);
        fastutil = new ObjectOpenCustomHashSet<>(size, (float) DEFAULT_LOAD_FACTOR, ByteArrays.HASH_STRATEGY);

        values = new byte[OPERATIONS][];

        int count = (int) (size * fillFraction);
        for (int i = 0; i < count; i++) {
            byte[] value = toBytes(i, payloadSize);
            vector128.put(value);
            vector64.put(value);
            pseudoVector.put(value);
            naive.put(value);
            fastutil.add(value);
        }

        int missing = (int) (findMissingFraction * OPERATIONS);
        for (int i = 0; i < missing; i++) {
            values[i] = toBytes(count + i, payloadSize);
        }
        for (int i = missing; i < OPERATIONS; i++) {
            values[i] = toBytes(ThreadLocalRandom.current().nextLong(0, count), payloadSize);
        }

        Collections.shuffle(Arrays.asList(values));
    }

    private byte[] toBytes(long value, int outputLength)
    {
        byte[] output = new byte[outputLength];
        byte[] bytes = Longs.toByteArray(value);
        Arrays.fill(output, (byte) 0);
        System.arraycopy(bytes, 0, output, output.length - bytes.length, bytes.length);

        return output;
    }

    @Benchmark
    public void benchmarkVector128()
    {
        for (byte[] value : values) {
            consume(vector128.find(value));
        }
    }

    @Benchmark
    public void benchmarkVector64()
    {
        for (byte[] value : values) {
            consume(vector64.find(value));
        }
    }

    @Benchmark
    public void benchmarkPseudoVector()
    {
        for (byte[] value : values) {
            consume(pseudoVector.find(value));
        }
    }

    @Benchmark
    public void benchmarkNaive()
    {
        for (byte[] value : values) {
            consume(naive.find(value));
        }
    }

    @Benchmark
    public void benchmarkFastutil()
    {
        for (byte[] value : values) {
            consume(fastutil.contains(value));
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private void consume(boolean value)
    {
    }

    record ComparableArray(byte[] value)
    {
        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            return Arrays.equals(this.value, ((ComparableArray) o).value);
        }

        @Override
        public int hashCode()
        {
            return Arrays.hashCode(value);
        }
    }
    
    public static void main(String[] args)
            throws RunnerException
    {
        BenchmarkRunner.benchmark(BenchmarkFind.class);
    }
}
