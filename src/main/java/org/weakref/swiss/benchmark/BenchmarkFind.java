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
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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

    @Param({"1000", "1000000"})
    public int size = 1000;

    @Param({"0.1", "0.9", "1"})
    public double fillFraction = 0.1;

    @Param({"0", "0.5", "1"})
    public double findMissingFraction = 1; // what percentage of find() operations will look for a missing item

    private long[] values;

    private SwissVector128 vector128;
    private SwissVector64 vector64;
    private SwissPseudoVector pseudoVector;
    private SwissNaive naive;
    private LongOpenHashSet fastutil;

    @Setup
    public void setup()
    {
        vector128 = new SwissVector128(size);
        vector64 = new SwissVector64(size);
        pseudoVector = new SwissPseudoVector(size);
        naive = new SwissNaive(size);
        fastutil = new LongOpenHashSet(size, (float) DEFAULT_LOAD_FACTOR);

        values = new long[OPERATIONS];

        int count = (int) (size * fillFraction);
        for (int i = 0; i < count; i++) {
            vector128.put(i);
            vector64.put(i);
            pseudoVector.put(i);
            naive.put(i);
            fastutil.add(i);
        }

        int missing = (int) (findMissingFraction * OPERATIONS);
        for (int i = 0; i < missing; i++) {
            values[i] = count + i;
        }
        for (int i = missing; i < OPERATIONS; i++) {
            values[i] = ThreadLocalRandom.current().nextLong(0, count);
        }

        Collections.shuffle(Longs.asList(values));
    }

    @Benchmark
    public void benchmarkVector128()
    {
        for (long value : values) {
            consume(vector128.find(value));
        }
    }

    @Benchmark
    public void benchmarkVector64()
    {
        for (long value : values) {
            consume(vector64.find(value));
        }
    }

    @Benchmark
    public void benchmarkPseudoVector()
    {
        for (long value : values) {
            consume(pseudoVector.find(value));
        }
    }

    @Benchmark
    public void benchmarkNaive()
    {
        for (long value : values) {
            consume(naive.find(value));
        }
    }

    @Benchmark
    public void benchmarkFastutil()
    {
        for (long value : values) {
            consume(fastutil.contains(value));
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private void consume(boolean value)
    {
    }

    public static void main(String[] args)
            throws RunnerException
    {
        BenchmarkRunner.benchmark(BenchmarkFind.class);
    }
}
