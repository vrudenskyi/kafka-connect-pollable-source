/**
 * Copyright  Vitalii Rudenskyi (vrudenskyi@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vrudenskyi.kafka.connect.source;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.common.Configurable;
import org.apache.kafka.connect.source.SourceRecord;

/**
 * Client that polls data from an external system and produces records
 * 
 * @author Vitalii Rudenskyi
 */
public interface PollableAPIClient extends Configurable, Closeable {
  
  
  public static final String INITIAL_OFFSET_CONFIG = "initial.offset";
  
  /**
   * 
   * Poll data for provided topic, partition, offset.
   * method is responsible for  appropriate offset updates
   * @param topic 
   * 
   * @param topic
   * @param partition
   * @param offset
   * @param itemsToPoll - target number of records to produce for a poll
   * @param stop - if true poll should exit asap 
   * @return
   *   List of  records
   * @throws APIClientException
   */
  public  List<SourceRecord> poll(String topic, Map<String, Object> partition, Map<String, Object> offset, int itemsToPoll, AtomicBoolean stop) throws APIClientException;

  
  /**
   * Returns available partitions based on client config
   * 
   * @return
   */
  public List<Map<String, Object>> partitions() throws APIClientException;


  /**
   * produces initial offset for a partition.
   * 
   * 
   * @param partition - one of partition from {@code PollableAPIClient.partitions()}
   * @return
   */
  public Map<String, Object> initialOffset(Map<String, Object> partition) throws APIClientException;
  
  
  /**
   * Close client and release any resources 
   */
  public void close();

  

}
