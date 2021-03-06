/**
 * Copyright (c) 2011, Cloudera, Inc. All Rights Reserved.
 *
 * Cloudera, Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the
 * License.
 */
package com.cloudera.crunch;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.newInputStreamSupplier;
import static org.junit.Assert.assertTrue;

import com.cloudera.crunch.DoFn;
import com.cloudera.crunch.Emitter;
import com.cloudera.crunch.PCollection;
import com.cloudera.crunch.PTable;
import com.cloudera.crunch.Pipeline;
import com.cloudera.crunch.impl.mr.MRPipeline;
import com.cloudera.crunch.type.PTypeFamily;
import com.cloudera.crunch.type.avro.AvroTypeFamily;
import com.cloudera.crunch.type.writable.WritableTypeFamily;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;

import org.junit.Test;

@SuppressWarnings("serial")
public class CollectionsTest {
  
  public static class CombineStringListFn extends CombineFn<String, Collection<String>> {
    @Override
    public Collection<String> combine(Iterable<Collection<String>> values) {
      Collection<String> rtn = Lists.newArrayList();
      for(Collection<String> list : values) {
        rtn.addAll(list);
      }
      return rtn;
    }      
  }
  
  public static PTable<String, Collection<String>> listOfCharcters(PCollection<String> lines, PTypeFamily typeFamily) {
     
    return lines.parallelDo(new DoFn<String, Pair<String, Collection<String>>>() {
      @Override
      public void process(String line, Emitter<Pair<String, Collection<String>>> emitter) {
        for (String word : line.split("\\s+")) {
          Collection<String> characters = Lists.newArrayList();
          for(char c : word.toCharArray()) {
            characters.add(String.valueOf(c));
          }
          emitter.emit(Pair.of(word, characters));
        }
      }
    }, typeFamily.tableOf(typeFamily.strings(), typeFamily.collections(typeFamily.strings())))
    .groupByKey()
    .combineValues(new CombineStringListFn());
  }
  
  @Test
  public void testWritables() throws IOException {
    run(new MRPipeline(CollectionsTest.class), WritableTypeFamily.getInstance());
  }

  @Test
  public void testAvro() throws IOException {
    run(new MRPipeline(CollectionsTest.class), AvroTypeFamily.getInstance());
  }
  
  public void run(Pipeline pipeline, PTypeFamily typeFamily) throws IOException {
    File input = File.createTempFile("shakes", "txt");
    input.deleteOnExit();
    Files.copy(newInputStreamSupplier(getResource("shakes.txt")), input);
    
    File output = File.createTempFile("output", "");
    String outputPath = output.getAbsolutePath();
    output.delete();
    
    PCollection<String> shakespeare = pipeline.readTextFile(input.getAbsolutePath());
    pipeline.writeTextFile(listOfCharcters(shakespeare, typeFamily), outputPath);
    pipeline.done();
    
    File outputFile = new File(output, "part-r-00000");
    List<String> lines = Files.readLines(outputFile, Charset.defaultCharset());
    boolean passed = false;
    for (String line : lines) {
      if(line.startsWith("yellow")) {
        passed = true;
        break;
      }
    }
    assertTrue(passed);

    output.deleteOnExit();
  }  
}
