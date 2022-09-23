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
import java.util.Calendar;

import com.vrudensk.kafka.connect.utils.DateTimeUtils;

/**
 * 
 * Helpers to manage timebased partitioning.
 * 
 * PROBLEM: too much data that can't be consumed by a single kafka-connect task and there is no appropriate field/filter for partitioning  
 * SOLUTION:  time-based partitioning - each partition will consume specific part of the hour:
 *  
 * if 2 partitions:
 *     0  - 0-29 minutes
 *     1  - 30-59
 *  
 * if 3 partitions:
 *     0  - 0-19 minutes
 *     1  - 20-39
 *     2  - 40-59
 * if 4 partitions:
 *     0  - 0-14 minutes
 *     1  - 15-29
 *     2  - 30-44
 *     3  - 45-59
 *
 * etc: 
 *  
 *    limitations: 
 *      - remainder of 60 % numberOfPartitions must be 0
 *      - max number of partitions 60 
 */

public class TimeBasedPartitionHelper {

  /**
   * Change specific part of ZonedDateTime
   * 
   * 
   * 
   * @param baseTime
   * @param field the given calendar field.
     @param value the value to be set for the given calendar field.
   * @return
   */
  public static ZonedDateTime setTimeField(ZonedDateTime baseTime, int field, int value) {
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(baseTime.toInstant().toEpochMilli());
    cal.set(field, value);
    return ZonedDateTime.ofInstant(cal.toInstant(), baseTime.getZone());
  }

  /**
   * return end date for bounded search: startDate + queryWindow
   * if partitioned (number of partitions > 1) checks partition boundaries 
   *  
   * 
   * @param startDate
   * @param numberOfPartitions
   * @param partitionId
   * @param queryWindow - query window  
   * @return
   */
  public static ZonedDateTime getEndDate(ZonedDateTime startDate, int queryWindow, int partitionId, int numberOfPartitions) {

    if (numberOfPartitions <= 1) {
      return startDate.plusMinutes(queryWindow);
    }
    if (60 % numberOfPartitions != 0)
      throw new IllegalArgumentException("remainder of 60 % numberOfPartitions must be 0");

    int partitionWindow = 60 / numberOfPartitions;
    int pQueryWindow = Math.min(partitionWindow, queryWindow);

    int pMinutesStart = partitionWindow * partitionId;
    int pMinutesEnd = pMinutesStart + partitionWindow;

    ZonedDateTime maxNewDate = setTimeField(startDate, Calendar.MINUTE, pMinutesEnd);

    return DateTimeUtils.min(maxNewDate, DateTimeUtils.min(startDate.plusMinutes(partitionWindow), startDate.plusMinutes(pQueryWindow)));
  }

  /**
   * returns next startDate 
   * 
   * if partitioned (number of partitions > 1) checks partition boundaries 
   * 
   * @param prevStartDate
   * @param numberOfPartitions
   * @param partitionId
   * @param queryWindow
   * @return
   */
  public static ZonedDateTime shiftStartDate(ZonedDateTime prevStartDate, int queryWindow, int partitionId, int numberOfPartitions) {

    if (numberOfPartitions <= 1) {
      return prevStartDate.plusMinutes(queryWindow);
    }
    if (60 % numberOfPartitions != 0)
      throw new IllegalArgumentException("remainder of 60 % numberOfPartitions must be 0");

    int partitionWindow = 60 / numberOfPartitions;
    int pQueryWindow = Math.min(partitionWindow, queryWindow);

    int pMinutesStart = partitionWindow * partitionId;
    int pMinutesEnd = pMinutesStart + partitionWindow;

    //next shift within bounds
    ZonedDateTime newDate = prevStartDate.plusMinutes(pQueryWindow);
    ZonedDateTime maxNewDate = setTimeField(prevStartDate, Calendar.MINUTE, pMinutesEnd);

    if (newDate.isEqual(maxNewDate) || newDate.isAfter(maxNewDate)) {
      //start next hour
      newDate = setTimeField(prevStartDate, Calendar.MINUTE, pMinutesStart).plusHours(1);

    }
    return newDate;
  }

  /**
   * if partitioned (number of partitions > 1) returns 'closest' time to appropriate partitioned time 
   * e.g. if time  DD-MM-YYYY 12:03:00:00  and numberOfPartitions = 2 ans partion id=1 it will return DD-MM-YYYY 12:30:00:00
   * 
   * @param initialOffset
   * @param partitionId
   * @param numberOfPartitions
   * @return
   */
  public static ZonedDateTime initStartDate(ZonedDateTime initialOffset, int partitionId, int numberOfPartitions) {

    if (numberOfPartitions <= 1) {
      return initialOffset;
    }
    if (60 % numberOfPartitions != 0)
      throw new IllegalArgumentException("remainder of 60 % numberOfPartitions must be 0");

    int partitionWindow = 60 / numberOfPartitions;
    int pMinutesStart = partitionWindow * partitionId;
    ZonedDateTime initstartDate = setTimeField(initialOffset, Calendar.MINUTE, pMinutesStart);
    return initstartDate;
  }

}
