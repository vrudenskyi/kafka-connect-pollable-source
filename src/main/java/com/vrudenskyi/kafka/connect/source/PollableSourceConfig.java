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

import java.util.Map;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

public class PollableSourceConfig extends AbstractConfig {

  public static final String POLL_INTERVAL_CONFIG = "poll.interval";
  public static final long POLL_INTERVAL_DEFAULT = 5000;

  public static final String POLL_CRON_CONFIG = "poll.cron";

  public static final String POLL_RETRIES_CONFIG = "poll.retries";
  public static final int POLL_RETRIES_DEFAULT = 3;

  public static final String POLL_BACKOFF_CONFIG = "poll.backoff";
  public static final long POLL_BACKOFF_DEFAULT = 30000L;

  public static final String POLL_SIZE_CONFIG = "poll.size";
  private static final int POLL_SIZE_DEFAULT = 1000;

  public static final String TOPIC_CONFIG = "topic";

  protected static ConfigDef baseConfigDef() {
    final ConfigDef configDef = new ConfigDef();
    addConfig(configDef);
    return configDef;
  }

  public static ConfigDef addConfig(ConfigDef config) {
    config
        .define(TOPIC_CONFIG, Type.STRING, ConfigDef.NO_DEFAULT_VALUE, Importance.HIGH, "Kafka topic to write data to")
        .define(POLL_INTERVAL_CONFIG, Type.LONG, POLL_INTERVAL_DEFAULT, Importance.MEDIUM, "Poll frequency in millis")
        .define(POLL_CRON_CONFIG, Type.STRING, null, Importance.LOW, "Poll cron expression. ")
        .define(POLL_RETRIES_CONFIG, Type.INT, POLL_RETRIES_DEFAULT, Importance.MEDIUM, "Number of retries before task fails")
        .define(POLL_BACKOFF_CONFIG, Type.LONG, POLL_BACKOFF_DEFAULT, Importance.MEDIUM, "Millis to wait before retry")
        .define(POLL_SIZE_CONFIG, Type.INT, POLL_SIZE_DEFAULT, Importance.MEDIUM, "Target poll size.");
    return config;

  }

  public static final ConfigDef CONFIG = baseConfigDef();

  public PollableSourceConfig(Map<String, ?> props) {
    super(CONFIG, props);
  }

  public PollableSourceConfig(ConfigDef configDef, Map<String, String> props) {
    super(configDef, props);
  }

}
