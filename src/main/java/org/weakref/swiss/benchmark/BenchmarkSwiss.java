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
import org.weakref.swiss.SwissPseudoVector;
import org.weakref.swiss.SwissVector;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgsAppend = {
        "--enable-preview",
        "--add-modules=jdk.incubator.vector",
        "-XX:+UnlockDiagnosticVMOptions",
//        "-XX:CompileCommand=print,*swiss*.*",
//        "-XX:PrintAssemblyOptions=intel"
})
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
@OperationsPerInvocation(BenchmarkSwiss.INSERTIONS)
public class BenchmarkSwiss
{
    public final static int INSERTIONS = 10_000;

    @Param({"1000"})
    public int size = 1000;

    private long[] data;

    @Setup
    public void setup()
    {
        data = new long[INSERTIONS];

        int count = size - 1;
        for (int i = 0; i < count; i++) {
            data[i] = ThreadLocalRandom.current().nextLong();
        }

        for (int i = count; i < INSERTIONS; i++) {
            data[i] = data[i % count];
        }
    }

    @Benchmark
    public void swissVector()
    {
        SwissVector table = new SwissVector(size);
        for (long value : data) {
            consume(table.put(value));
        }
    }

    @Benchmark
    public void swissPseudoVector()
    {
        SwissPseudoVector table = new SwissPseudoVector(size);
        for (long value : data) {
            consume(table.put(value));
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private void consume(boolean value)
    {
    }

    public static void main(String[] args)
            throws RunnerException
    {
        BenchmarkSwiss runner = new BenchmarkSwiss();
//        runner.setup();
//        runner.swissPseudoVector();
//        runner.swissVector();

        BenchmarkRunner.benchmark(BenchmarkSwiss.class);
    }
}
