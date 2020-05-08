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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

public abstract class PollableSourceTask extends SourceTask {
  private static final Logger log = LoggerFactory.getLogger(PollableSourceTask.class);
  protected static final ZonedDateTime ZERO_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.systemDefault());


  protected AtomicBoolean stop;

  private long pollInterval;
  private int pollSize;
  private long pollBackoff;
  private int pollRetries;

  protected String topic;
  private ExecutionTime cronExecutionTime;

  @Override
  public void start(Map<String, String> props) {
    PollableSourceConfig config = new PollableSourceConfig(props);
    this.topic = config.getString(PollableSourceConfig.TOPIC_CONFIG);
    this.pollInterval = config.getLong(PollableSourceConfig.POLL_INTERVAL_CONFIG);
    this.pollSize = config.getInt(PollableSourceConfig.POLL_SIZE_CONFIG);
    this.pollBackoff = config.getLong(PollableSourceConfig.POLL_BACKOFF_CONFIG);
    this.pollRetries = config.getInt(PollableSourceConfig.POLL_RETRIES_CONFIG);
    String cronExpr = config.getString(PollableSourceConfig.POLL_CRON_CONFIG);
    if (StringUtils.isNotBlank(cronExpr)) {
      CronParser cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
      this.cronExecutionTime = ExecutionTime.forCron(cronParser.parse(cronExpr));
    }
    stop = new AtomicBoolean(false);
    log.trace("Started connector for: {}", this.topic);
  }

  protected abstract void pollData(int size, List<SourceRecord> result);

  @Override
  public List<SourceRecord> poll() throws InterruptedException {
    log.trace("Start polling for new data for topic: {}", this.topic);
    if (stop == null) {
      throw new ConnectException("Task is not not properly started");
    }
    List<SourceRecord> result = new ArrayList<>();

    long pollStarted = System.currentTimeMillis();
    int retriesLeft = this.pollRetries;

    // call pollData with respect to poll interval, retries   
    while (true) {
      try {
        pollData(this.pollSize, result);
      } catch (Exception e) {
        retriesLeft--;
        if (retriesLeft < 0) {
          throw new ConnectException("Failed to poll data after all (" + this.pollRetries + ") retries", e);
        }

        log.warn("Failed to poll data. Will retry " + retriesLeft + " more times", e);
        if (pollBackoff > 0) {
          log.debug("Sleep for {} millis before retry...", pollBackoff);
          sleep(pollBackoff);
        }

      }

      //enough data collected
      if (stop.get() || result.size() >= pollSize) {
        break;
      }

      //exit if any data available and beyond pollInterval 
      if (System.currentTimeMillis() > pollStarted + pollInterval && result.size() > 0) {
        break;
      }

      ///wait until stopped or ready for the next poll
      log.debug("Task for topic {} wait till next poll {}", this.topic, pollInterval);
      sleep(pollInterval);
      if (stop.get()) {
        log.debug("Stopped signal received. Exit immediately.");
        break;
      }
    }

    log.debug("Poll complete for topic: {},  pollTime: {},  pollSize: {} of {}", this.topic, (System.currentTimeMillis() - pollStarted), result.size(), this.pollSize);
    return result;

  }
  
  
  protected ZonedDateTime getNextTimeToExecute(long lastExecuted) {
    if (cronExecutionTime == null) {
      return ZERO_TIME;
    }
    ZonedDateTime nextExecution = cronExecutionTime.nextExecution(ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastExecuted), ZoneId.systemDefault())).get();
    return nextExecution;
  }
  
  protected boolean isCronConfigured() {
    return cronExecutionTime != null;
  }
  
  

  @Override
  public void stop() {
    if (stop != null) {
      stop.set(true);
    }
  }

  public void sleep(long millis) {
    long wakeuptime = System.currentTimeMillis() + millis;
    while (true) {
      try {
        Thread.sleep(1000L);
      } catch (InterruptedException e) {
        log.debug("Sleep interrupted");
      }
      if (System.currentTimeMillis() >= wakeuptime || stop.get())
        break;

    }
  }

}
