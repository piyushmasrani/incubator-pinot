/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.common.lineage;

import com.google.common.base.Preconditions;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.helix.ZNRecord;


/**
 * Class to represent segment lineage information.
 *
 * Segment lineage keeps the metadata required for supporting m -> n segment replacement. Segment lineage is serialized
 * into a znode and stored in a helix property store (zookeeper). This metadata will be used by brokers to make sure
 * that the routing does not pick the segments with the duplicate data.
 *
 * NOTE: Update for the underlying segment lineage znode needs to happen with read-modify-write block to guarantee the
 * atomic update because this metadata can be modified concurrently (e.g. task scheduler tries to add entries after
 * scheduling new tasks while minion task tries to update the state of the existing entry)
 */
public class SegmentLineage {
  private static final String COMMA_SEPARATOR = ",";

  private final String _tableNameWithType;
  private final Map<String, LineageEntry> _lineageEntries;

  public SegmentLineage(String tableNameWithType) {
    _tableNameWithType = tableNameWithType;
    _lineageEntries = new HashMap<>();
  }

  public SegmentLineage(String tableNameWithType, Map<String, LineageEntry> lineageEntries) {
    _tableNameWithType = tableNameWithType;
    _lineageEntries = lineageEntries;
  }

  public String getTableNameWithType() {
    return _tableNameWithType;
  }

  /**
   * Add lineage entry to the segment lineage metadata
   * @param lineageEntry a lineage entry
   * @return the id for the input lineage entry for the access
   */
  public String addLineageEntry(LineageEntry lineageEntry) {
    String lineageId = generateLineageId();
    _lineageEntries.put(lineageId, lineageEntry);
    return lineageId;
  }

  /**
   * Retrieve lineage entry
   * @param lineageEntryId the id for the lineage entry
   * @return the lineage entry for the given lineage entry id
   */
  public LineageEntry getLineageEntry(String lineageEntryId) {
    return _lineageEntries.get(lineageEntryId);
  }

  /**
   * Retrieve the lineage ids for all lineage entries
   * @return lineage entry ids
   */
  public Set<String> getLineageEntryIds() {
    return _lineageEntries.keySet();
  }

  /**
   * Delete lineage entry
   * @param lineageEntryId the id for the lineage entry
   */
  public void deleteLineageEntry(String lineageEntryId) {
    _lineageEntries.remove(lineageEntryId);
  }

  /**
   * Convert ZNRecord to segment lineage
   * @param record ZNRecord representation of the segment lineage
   * @return the segment lineage object
   */
  public static SegmentLineage fromZNRecord(ZNRecord record) {
    String tableNameWithType = record.getId();
    Map<String, LineageEntry> lineageEntries = new HashMap<>();
    Map<String, List<String>> listFields = record.getListFields();
    for (Map.Entry<String, List<String>> listField : listFields.entrySet()) {
      String lineageId = listField.getKey();
      List<String> value = listField.getValue();
      Preconditions.checkState(value.size() == 4);
      List<String> segmentsFrom = Arrays.asList(value.get(0).split(COMMA_SEPARATOR));
      List<String> segmentsTo = Arrays.asList(value.get(1).split(COMMA_SEPARATOR));
      LineageEntryState state = LineageEntryState.valueOf(value.get(2));
      long timestamp = Long.parseLong(value.get(3));
      lineageEntries.put(lineageId, new LineageEntry(segmentsFrom, segmentsTo, state, timestamp));
    }
    return new SegmentLineage(tableNameWithType, lineageEntries);
  }

  /**
   * Convert the segment lineage object to the ZNRecord
   * @return ZNRecord representation of the segment lineage
   */
  public ZNRecord toZNRecord() {
    ZNRecord znRecord = new ZNRecord(_tableNameWithType);
    for (Map.Entry<String, LineageEntry> entry : _lineageEntries.entrySet()) {
      LineageEntry lineageEntry = entry.getValue();
      String segmentsFrom = String.join(",", lineageEntry.getSegmentsFrom());
      String segmentsTo = String.join(",", lineageEntry.getSegmentsTo());
      String state = lineageEntry.getState().toString();
      String timestamp = Long.toString(lineageEntry.getTimestamp());
      List<String> listEntry = Arrays.asList(segmentsFrom, segmentsTo, state, timestamp);
      znRecord.setListField(entry.getKey(), listEntry);
    }
    return znRecord;
  }

  private String generateLineageId() {
    return UUID.randomUUID().toString();
  }
}
