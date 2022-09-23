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

import org.apache.kafka.common.config.ConfigDef;

public class PollableAPIClientSourceConfig extends PollableSourceConfig {

  public static final String APICLIENT_CLASS_CONFIG = "apiclient.class";
  public static final String RESET_OFFSETS_CONFIG = "reset.offsets";
  public static final String POLL_PARTITIONS_EVENLY_CONFIG = "poll.partitions.evenly";

  static final ConfigDef CONFIG = PollableSourceConfig.baseConfigDef()
      .define(APICLIENT_CLASS_CONFIG, ConfigDef.Type.CLASS, ConfigDef.NO_DEFAULT_VALUE, ConfigDef.Importance.HIGH, "Implementation of PollableAPIClient")
      .define(RESET_OFFSETS_CONFIG, ConfigDef.Type.BOOLEAN, Boolean.FALSE, ConfigDef.Importance.LOW, "Reset offsets")
      .define(POLL_PARTITIONS_EVENLY_CONFIG, ConfigDef.Type.BOOLEAN, Boolean.TRUE, ConfigDef.Importance.LOW, "Poll partitions evenly. default: true");

  public static ConfigDef configDef() {
    return CONFIG;
  }

  public PollableAPIClientSourceConfig(Map<String, String> props) {
    super(CONFIG, props);
  }

}
