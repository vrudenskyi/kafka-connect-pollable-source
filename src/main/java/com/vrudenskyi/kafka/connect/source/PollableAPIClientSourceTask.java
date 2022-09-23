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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollableAPIClientSourceTask extends PollableSourceTask {

  protected static final String CRC32_PARTITIONS_FOR_TASK_CONFIG = "_______crc32_partitions_for_task";
  protected static final String LAST_EXECUTED_TIME_CONFIG = "_______last_execution_time_for_partition";

  private static final Logger log = LoggerFactory.getLogger(PollableAPIClientSourceTask.class);

  private PollableAPIClient apiClient;
  private Boolean pollEvenly;
  private List<Map<String, Object>> partitions;
  private Map<Map<String, Object>, Map<String, Object>> offsets;

  private Map<String, Object> currentPartition;
  private Map<String, Object> currentOffset;

  private Iterator<Map<String, Object>> partitionIterator;

  @Override
  public String version() {
    return PollableAPIClientSourceConnector.version;
  }

  public static String partitionCrc32(Map<String, Object> p) {

    //build String from map:
    StringBuilder sb = new StringBuilder();
    for (Entry<String, Object> e : p.entrySet()) {
      sb.append(e.getKey()).append("=").append(e.getValue());
    }
    byte[] bytes = sb.toString().getBytes();

    Checksum checksum = new CRC32();
    checksum.update(bytes, 0, bytes.length);
    long checksumValue = checksum.getValue();
    return String.valueOf(checksumValue);

  }

  @Override
  public void start(Map<String, String> props) {
    PollableAPIClientSourceConfig config = new PollableAPIClientSourceConfig(props);
    this.apiClient = config.getConfiguredInstance(PollableAPIClientSourceConfig.APICLIENT_CLASS_CONFIG, PollableAPIClient.class);
    this.pollEvenly = config.getBoolean(PollableAPIClientSourceConfig.POLL_PARTITIONS_EVENLY_CONFIG);
    
    log.info("Starting PollableAPIClient task for {}", this.apiClient.getClass().getName());
    

    List<Map<String, Object>> partitionsFromClient;
    try {
      partitionsFromClient = apiClient.partitions();
    } catch (APIClientException e) {
      throw new ConnectException(this.apiClient.getClass().getSimpleName() + " :failed to retrieve partitions from client: ", e);
    }

    if (props.containsKey(CRC32_PARTITIONS_FOR_TASK_CONFIG)) {
      Set<String> pList = new HashSet<>(Arrays.asList(props.get(CRC32_PARTITIONS_FOR_TASK_CONFIG).split(",")));
      this.partitions = new ArrayList<>(pList.size());
      for (Map<String, Object> pfc : partitionsFromClient) {
        if (pList.contains(partitionCrc32(pfc))) {
          this.partitions.add(pfc);
        }

      }
    } else {
      this.partitions = partitionsFromClient;
    }

    if (partitions.size() == 0) {
      throw new ConnectException("no partitions from client: " + this.apiClient);
    }
    log.debug("Task({}) is starting for partitions: {}", this.apiClient.getClass().getSimpleName(),  this.partitions);

    // read stored offsets
    this.offsets = context.offsetStorageReader().offsets(partitions);
    if (this.offsets == null || config.getBoolean(PollableAPIClientSourceConfig.RESET_OFFSETS_CONFIG)) {
      this.offsets = new HashMap<>(partitions.size());
    }
    for (Map<String, Object> partition : partitions) {
      if (!this.offsets.containsKey(partition) || this.offsets.get(partition) == null) {
        try {
          this.offsets.put(partition, apiClient.initialOffset(partition));
        } catch (APIClientException e) {
          throw new ConnectException(this.apiClient.getClass().getSimpleName() + " :failed to create initial offset  for partition: " + partition, e);
        }
      }
    }

    partitionIterator = partitions.iterator();
    currentPartition = partitionIterator.next();
    currentOffset = this.offsets.get(currentPartition);

    super.start(props);
    log.info("PollableAPIClientSourceTask started for client: {}", this.apiClient.getClass().getSimpleName());

  }

  @Override
  protected void pollData(int batchSize, List<SourceRecord> data) {

    if (data == null) {
      throw new NullPointerException("list of SourceRecords is null");
    }
    if (batchSize <= data.size()) {
      return;
    }

    //query the data
    // poll until all partitions processed or reached batchSize 
    boolean done = false;
    int partitionsToRead = partitions.size();
    while (!done) {
      int itemsToPoll = batchSize - data.size();
      Long lastExecuted = (Long) currentOffset.getOrDefault(LAST_EXECUTED_TIME_CONFIG, Long.valueOf(0L));
      ZonedDateTime nextExecutionTime = getNextTimeToExecute(lastExecuted);
      if (!isCronConfigured() || nextExecutionTime.isBefore(ZonedDateTime.now())) {
        try {
          log.debug("Client {}.poll(partition={},  offset={}, itemsToPoll={}", apiClient.getClass().getSimpleName(), currentPartition, currentOffset, itemsToPoll);
          List<SourceRecord> pollResult = apiClient.poll(topic, currentPartition, currentOffset, itemsToPoll, stop);
          log.debug(">>> {} poll result: {} | (newOffset={}, topic={})", apiClient.getClass().getSimpleName(), pollResult.size(), currentOffset, topic);
          data.addAll(pollResult);
          if (isCronConfigured() && pollResult.size() < batchSize) { //update offset only is result < batchsize because it will stop polling until the next cron time  
            currentOffset.put(LAST_EXECUTED_TIME_CONFIG, Long.valueOf(System.currentTimeMillis()));
          }

        } catch (APIClientException e) {
          throw new ConnectException(e.getMessage(), e.getCause());
        }
      } else {
        log.debug("{}: execution skipped for partition: {}, next executionTime: {}", apiClient.getClass().getSimpleName(),  currentPartition, nextExecutionTime);
      }
      
      //if fed-up and configured poll.partitions.evenly = false each partition will be read until the end before switch to the next one       
      if (data.size() >= batchSize & !this.pollEvenly) {
        log.debug("{}: skip rolling partition to resume current with the next poll", apiClient.getClass().getSimpleName());
        return;
      }
      

      if (!partitionIterator.hasNext()) {
        //reinit partitions iterator 
        partitionIterator = partitions.iterator();
      }

      //next partition
      currentPartition = partitionIterator.next();
      partitionsToRead--;
      currentOffset = offsets.get(currentPartition);

      if (stop != null && stop.get()) {
        log.debug("{}: Stop signal !!! Stop processing partitions and exit pollData now", apiClient.getClass().getSimpleName());
        done = true;
      }

      //done if size > batchSize  OR  all partitions was read within one poll
      done = done || data.size() >= batchSize || partitionsToRead <= 0;

    }

    log.debug("{}.pollData DONE: ({} of {}) => {}", apiClient.getClass().getSimpleName(), data.size(), batchSize, this.topic);

  }

  @Override
  public void stop() {
    super.stop();
    apiClient.close();
  }

}
