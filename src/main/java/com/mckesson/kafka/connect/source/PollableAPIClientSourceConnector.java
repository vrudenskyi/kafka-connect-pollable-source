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
package com.mckesson.kafka.connect.source;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceConnector;
import org.apache.kafka.connect.util.ConnectorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollableAPIClientSourceConnector extends SourceConnector {

  private static final Logger log = LoggerFactory.getLogger(PollableAPIClientSourceConnector.class);
  static String version = "unknown";
  private String connectorName;
  private PollableAPIClientSourceConfig config;

  static {

    try {
      Properties props = new Properties();
      props.load(PollableAPIClientSourceConnector.class.getResourceAsStream("/kafka-connect-common.properties"));
      version = props.getProperty("version", version).trim();
    } catch (IOException e) {
      log.warn("Failed to read version", e);
    }

  }

  @Override
  public String version() {
    return version;
  }

  @Override
  public void start(Map<String, String> props) {
    config = new PollableAPIClientSourceConfig(props);
    String className = config.getClass(PollableAPIClientSourceConfig.APICLIENT_CLASS_CONFIG).getSimpleName();
    String topicName = config.getString(PollableAPIClientSourceConfig.TOPIC_CONFIG);
    this.connectorName = className + "[" + topicName + "]";
    
    log.info("Started connector: {}", connectorName);

  }

  @Override
  public Class<? extends Task> taskClass() {
    return PollableAPIClientSourceTask.class;
  }

  @Override
  public List<Map<String, String>> taskConfigs(int maxTasks) {
    if (maxTasks > 1) {
      log.debug("get list of available partitions from apiClient.");
      PollableAPIClient apiClient = config.getConfiguredInstance(PollableAPIClientSourceConfig.APICLIENT_CLASS_CONFIG, PollableAPIClient.class);
      List<Map<String, Object>> allFromClient;
      try {
        allFromClient = apiClient.partitions();
        apiClient.close();
      } catch (APIClientException e) {
        log.error("Failed to obtain partitions from apiClient", e);
        throw new ConnectException("Failed to obtain partitions from apiClient");
      }
      int numGroups = Math.min(allFromClient.size(), maxTasks);
      List<List<Map<String, Object>>> partitionsGrouped = ConnectorUtils.groupPartitions(allFromClient, numGroups);
      List<Map<String, String>> taskConfigs = new ArrayList<>(partitionsGrouped.size());

      //if multiply partitions from client it calculates CRC32 of each partition and adds calulated CRC for each Task
      //later, each task  will get full list of partitions from apiClient and will 'know' its partitions based on CRC    
      for (List<Map<String, Object>> taskPts : partitionsGrouped) {
        Map<String, String> taskProps = new HashMap<>(config.originalsStrings());
        List<String> partitionIds = taskPts.stream().map(itm -> PollableAPIClientSourceTask.partitionCrc32(itm)).collect(Collectors.toList());
        taskProps.put(PollableAPIClientSourceTask.CRC32_PARTITIONS_FOR_TASK_CONFIG, StringUtils.join(partitionIds, ","));
        taskConfigs.add(taskProps);
      }
      return taskConfigs;
    }

    return Arrays.asList(config.originalsStrings());
  }

  @Override
  public void stop() {
    log.info("Stopped connector: {}", connectorName);
  }

  @Override
  public ConfigDef config() {
    return PollableAPIClientSourceConfig.configDef();
  }

}
