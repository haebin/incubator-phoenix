/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.end2end;

import static org.apache.phoenix.jdbc.PhoenixDatabaseMetaData.TYPE_SCHEMA;
import static org.apache.phoenix.jdbc.PhoenixDatabaseMetaData.TYPE_SEQUENCE;
import static org.apache.phoenix.jdbc.PhoenixDatabaseMetaData.TYPE_TABLE;
import static org.apache.phoenix.util.TestUtil.ATABLE_NAME;
import static org.apache.phoenix.util.TestUtil.ATABLE_SCHEMA_NAME;
import static org.apache.phoenix.util.TestUtil.BTABLE_NAME;
import static org.apache.phoenix.util.TestUtil.CUSTOM_ENTITY_DATA_FULL_NAME;
import static org.apache.phoenix.util.TestUtil.CUSTOM_ENTITY_DATA_NAME;
import static org.apache.phoenix.util.TestUtil.CUSTOM_ENTITY_DATA_SCHEMA_NAME;
import static org.apache.phoenix.util.TestUtil.GROUPBYTEST_NAME;
import static org.apache.phoenix.util.TestUtil.MDTEST_NAME;
import static org.apache.phoenix.util.TestUtil.MDTEST_SCHEMA_NAME;
import static org.apache.phoenix.util.TestUtil.PHOENIX_JDBC_URL;
import static org.apache.phoenix.util.TestUtil.PTSDB_NAME;
import static org.apache.phoenix.util.TestUtil.STABLE_NAME;
import static org.apache.phoenix.util.TestUtil.TEST_PROPERTIES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;

import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.FirstKeyOnlyFilter;
import org.apache.hadoop.hbase.io.encoding.DataBlockEncoding;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.phoenix.coprocessor.GroupedAggregateRegionObserver;
import org.apache.phoenix.coprocessor.ServerCachingEndpointImpl;
import org.apache.phoenix.coprocessor.UngroupedAggregateRegionObserver;
import org.apache.phoenix.exception.SQLExceptionCode;
import org.apache.phoenix.jdbc.PhoenixConnection;
import org.apache.phoenix.jdbc.PhoenixDatabaseMetaData;
import org.apache.phoenix.schema.ColumnNotFoundException;
import org.apache.phoenix.schema.PDataType;
import org.apache.phoenix.schema.PTable.ViewType;
import org.apache.phoenix.schema.PTableType;
import org.apache.phoenix.schema.ReadOnlyTableException;
import org.apache.phoenix.schema.TableNotFoundException;
import org.apache.phoenix.util.PhoenixRuntime;
import org.apache.phoenix.util.SchemaUtil;
import org.apache.phoenix.util.StringUtil;
import org.apache.phoenix.util.TestUtil;
import org.junit.Test;


public class QueryDatabaseMetaDataTest extends BaseClientManagedTimeTest {

    @Test
    public void testTableMetadataScan() throws SQLException {
        long ts = nextTimestamp();
        ensureTableCreated(getUrl(), ATABLE_NAME, null, ts);
        ensureTableCreated(getUrl(), STABLE_NAME, null, ts);
        ensureTableCreated(getUrl(), CUSTOM_ENTITY_DATA_FULL_NAME, null, ts);
        
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 5));
        Connection conn = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        DatabaseMetaData dbmd = conn.getMetaData();
        String aTableName = StringUtil.escapeLike(TestUtil.ATABLE_NAME);
        String aSchemaName = TestUtil.ATABLE_SCHEMA_NAME;
        ResultSet rs = dbmd.getTables(null, aSchemaName, aTableName, null);
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_NAME"),aTableName);
        assertEquals(PTableType.TABLE.toString(), rs.getString("TABLE_TYPE"));
        assertEquals(rs.getString(3),aTableName);
        assertEquals(PTableType.TABLE.toString(), rs.getString(4));
        assertFalse(rs.next());
        
        rs = dbmd.getTables(null, null, null, null);
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),TYPE_SCHEMA);
        assertEquals(rs.getString("TABLE_NAME"),TYPE_TABLE);
        assertEquals(PTableType.SYSTEM.toString(), rs.getString("TABLE_TYPE"));
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),TYPE_SCHEMA);
        assertEquals(rs.getString("TABLE_NAME"),TYPE_SEQUENCE);
        assertEquals(PTableType.SYSTEM.toString(), rs.getString("TABLE_TYPE"));
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(rs.getString("TABLE_NAME"),ATABLE_NAME);
        assertEquals(PTableType.TABLE.toString(), rs.getString("TABLE_TYPE"));
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(rs.getString("TABLE_NAME"),STABLE_NAME);
        assertEquals(PTableType.TABLE.toString(), rs.getString("TABLE_TYPE"));
        assertTrue(rs.next());
        assertEquals(CUSTOM_ENTITY_DATA_SCHEMA_NAME, rs.getString("TABLE_SCHEM"));
        assertEquals(CUSTOM_ENTITY_DATA_NAME, rs.getString("TABLE_NAME"));
        assertEquals(PTableType.TABLE.toString(), rs.getString("TABLE_TYPE"));

        rs = dbmd.getTables(null, CUSTOM_ENTITY_DATA_SCHEMA_NAME, CUSTOM_ENTITY_DATA_NAME, null);
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),CUSTOM_ENTITY_DATA_SCHEMA_NAME);
        assertEquals(rs.getString("TABLE_NAME"),CUSTOM_ENTITY_DATA_NAME);
        assertEquals(PTableType.TABLE.toString(), rs.getString("TABLE_TYPE"));
        assertFalse(rs.next());
        
        try {
            rs.getString("RANDOM_COLUMN_NAME");
            fail();
        } catch (ColumnNotFoundException e) {
            // expected
        }
        assertFalse(rs.next());
        
        rs = dbmd.getTables(null, "", "_TABLE", new String[] {PTableType.TABLE.toString()});
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(rs.getString("TABLE_NAME"),ATABLE_NAME);
        assertEquals(PTableType.TABLE.toString(), rs.getString("TABLE_TYPE"));
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(rs.getString("TABLE_NAME"),STABLE_NAME);
        assertEquals(PTableType.TABLE.toString(), rs.getString("TABLE_TYPE"));
        assertFalse(rs.next());
    }

    @Test
    public void testSchemaMetadataScan() throws SQLException {
        long ts = nextTimestamp();
        ensureTableCreated(getUrl(), CUSTOM_ENTITY_DATA_FULL_NAME, null, ts);
        ensureTableCreated(getUrl(), PTSDB_NAME, null, ts);
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 5));
        Connection conn = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        DatabaseMetaData dbmd = conn.getMetaData();
        ResultSet rs;
        rs = dbmd.getSchemas(null, CUSTOM_ENTITY_DATA_SCHEMA_NAME);
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),CUSTOM_ENTITY_DATA_SCHEMA_NAME);
        assertEquals(rs.getString("TABLE_CATALOG"),null);
        assertFalse(rs.next());

        rs = dbmd.getSchemas(null, null);
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(rs.getString("TABLE_CATALOG"),null);
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),CUSTOM_ENTITY_DATA_SCHEMA_NAME);
        assertEquals(rs.getString("TABLE_CATALOG"),null);
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),PhoenixDatabaseMetaData.TYPE_SCHEMA);
        assertEquals(rs.getString("TABLE_CATALOG"),null);
        assertFalse(rs.next());
    }

    @Test
    public void testColumnMetadataScan() throws SQLException {
        long ts = nextTimestamp();
        ensureTableCreated(getUrl(), MDTEST_NAME, null, ts);
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 5));
        Connection conn = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        DatabaseMetaData dbmd = conn.getMetaData();
        ResultSet rs;
        rs = dbmd.getColumns(null, "", MDTEST_NAME, null);
        assertTrue(rs.next());

        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(MDTEST_NAME, rs.getString("TABLE_NAME"));
        assertEquals(null, rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("id"), rs.getString("COLUMN_NAME"));
        assertEquals(DatabaseMetaData.attributeNoNulls, rs.getShort("NULLABLE"));
        assertEquals(PDataType.CHAR.getSqlType(), rs.getInt("DATA_TYPE"));
        assertEquals(1, rs.getInt("ORDINAL_POSITION"));
        assertEquals(1, rs.getInt("COLUMN_SIZE"));
        assertEquals(0, rs.getInt("DECIMAL_DIGITS"));

        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(MDTEST_NAME, rs.getString("TABLE_NAME"));
        assertEquals(SchemaUtil.normalizeIdentifier("a"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col1"), rs.getString("COLUMN_NAME"));
        assertEquals(DatabaseMetaData.attributeNullable, rs.getShort("NULLABLE"));
        assertEquals(PDataType.INTEGER.getSqlType(), rs.getInt("DATA_TYPE"));
        assertEquals(2, rs.getInt("ORDINAL_POSITION"));
        assertEquals(10, rs.getInt("COLUMN_SIZE"));
        assertEquals(0, rs.getInt("DECIMAL_DIGITS"));

        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(MDTEST_NAME, rs.getString("TABLE_NAME"));
        assertEquals(SchemaUtil.normalizeIdentifier("b"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col2"), rs.getString("COLUMN_NAME"));
        assertEquals(DatabaseMetaData.attributeNullable, rs.getShort("NULLABLE"));
        assertEquals(PDataType.LONG.getSqlType(), rs.getInt("DATA_TYPE"));
        assertEquals(3, rs.getInt("ORDINAL_POSITION"));
        assertEquals(19, rs.getInt("COLUMN_SIZE"));
        assertEquals(0, rs.getInt("DECIMAL_DIGITS"));

        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(MDTEST_NAME, rs.getString("TABLE_NAME"));
        assertEquals(SchemaUtil.normalizeIdentifier("b"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col3"), rs.getString("COLUMN_NAME"));
        assertEquals(DatabaseMetaData.attributeNullable, rs.getShort("NULLABLE"));
        assertEquals(PDataType.DECIMAL.getSqlType(), rs.getInt("DATA_TYPE"));
        assertEquals(4, rs.getInt("ORDINAL_POSITION"));
        assertEquals(PDataType.MAX_PRECISION, rs.getInt("COLUMN_SIZE"));
        assertEquals(0, rs.getInt("DECIMAL_DIGITS"));

        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(MDTEST_NAME, rs.getString("TABLE_NAME"));
        assertEquals(SchemaUtil.normalizeIdentifier("b"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col4"), rs.getString("COLUMN_NAME"));
        assertEquals(DatabaseMetaData.attributeNullable, rs.getShort("NULLABLE"));
        assertEquals(PDataType.DECIMAL.getSqlType(), rs.getInt("DATA_TYPE"));
        assertEquals(5, rs.getInt("ORDINAL_POSITION"));
        assertEquals(5, rs.getInt("COLUMN_SIZE"));
        assertEquals(0, rs.getInt("DECIMAL_DIGITS"));

        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(MDTEST_NAME, rs.getString("TABLE_NAME"));
        assertEquals(SchemaUtil.normalizeIdentifier("b"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col5"), rs.getString("COLUMN_NAME"));
        assertEquals(DatabaseMetaData.attributeNullable, rs.getShort("NULLABLE"));
        assertEquals(PDataType.DECIMAL.getSqlType(), rs.getInt("DATA_TYPE"));
        assertEquals(6, rs.getInt("ORDINAL_POSITION"));
        assertEquals(6, rs.getInt("COLUMN_SIZE"));
        assertEquals(3, rs.getInt("DECIMAL_DIGITS"));

        assertFalse(rs.next());

        // Look up only columns in a column family
        rs = dbmd.getColumns(SchemaUtil.normalizeIdentifier("a"), "", MDTEST_NAME, null);
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(MDTEST_NAME, rs.getString("TABLE_NAME"));
        assertEquals(SchemaUtil.normalizeIdentifier("a"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col1"), rs.getString("COLUMN_NAME"));
        assertEquals(DatabaseMetaData.attributeNullable, rs.getShort("NULLABLE"));
        assertEquals(PDataType.INTEGER.getSqlType(), rs.getInt("DATA_TYPE"));
        assertEquals(2, rs.getInt("ORDINAL_POSITION"));
        assertEquals(10, rs.getInt("COLUMN_SIZE"));
        assertEquals(0, rs.getInt("DECIMAL_DIGITS"));

        assertFalse(rs.next());

        // Look up KV columns in a column family
        rs = dbmd.getColumns("%", "", MDTEST_NAME, null);
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(MDTEST_NAME, rs.getString("TABLE_NAME"));
        assertEquals(SchemaUtil.normalizeIdentifier("a"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col1"), rs.getString("COLUMN_NAME"));
        assertEquals(DatabaseMetaData.attributeNullable, rs.getShort("NULLABLE"));
        assertEquals(PDataType.INTEGER.getSqlType(), rs.getInt("DATA_TYPE"));
        assertEquals(2, rs.getInt("ORDINAL_POSITION"));
        assertEquals(10, rs.getInt("COLUMN_SIZE"));
        assertEquals(0, rs.getInt("DECIMAL_DIGITS"));

        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(MDTEST_NAME, rs.getString("TABLE_NAME"));
        assertEquals(SchemaUtil.normalizeIdentifier("b"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col2"), rs.getString("COLUMN_NAME"));
        assertEquals(DatabaseMetaData.attributeNullable, rs.getShort("NULLABLE"));
        assertEquals(PDataType.LONG.getSqlType(), rs.getInt("DATA_TYPE"));
        assertEquals(3, rs.getInt("ORDINAL_POSITION"));
        assertEquals(19, rs.getInt("COLUMN_SIZE"));
        assertEquals(0, rs.getInt("DECIMAL_DIGITS"));

        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(MDTEST_NAME, rs.getString("TABLE_NAME"));
        assertEquals(SchemaUtil.normalizeIdentifier("b"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col3"), rs.getString("COLUMN_NAME"));
        assertEquals(DatabaseMetaData.attributeNullable, rs.getShort("NULLABLE"));
        assertEquals(PDataType.DECIMAL.getSqlType(), rs.getInt("DATA_TYPE"));
        assertEquals(4, rs.getInt("ORDINAL_POSITION"));
        assertEquals(PDataType.MAX_PRECISION, rs.getInt("COLUMN_SIZE"));
        assertEquals(0, rs.getInt("DECIMAL_DIGITS"));

        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(MDTEST_NAME, rs.getString("TABLE_NAME"));
        assertEquals(SchemaUtil.normalizeIdentifier("b"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col4"), rs.getString("COLUMN_NAME"));
        assertEquals(DatabaseMetaData.attributeNullable, rs.getShort("NULLABLE"));
        assertEquals(PDataType.DECIMAL.getSqlType(), rs.getInt("DATA_TYPE"));
        assertEquals(5, rs.getInt("ORDINAL_POSITION"));
        assertEquals(5, rs.getInt("COLUMN_SIZE"));
        assertEquals(0, rs.getInt("DECIMAL_DIGITS"));

        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(MDTEST_NAME, rs.getString("TABLE_NAME"));
        assertEquals(SchemaUtil.normalizeIdentifier("b"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col5"), rs.getString("COLUMN_NAME"));
        assertEquals(DatabaseMetaData.attributeNullable, rs.getShort("NULLABLE"));
        assertEquals(PDataType.DECIMAL.getSqlType(), rs.getInt("DATA_TYPE"));
        assertEquals(6, rs.getInt("ORDINAL_POSITION"));
        assertEquals(6, rs.getInt("COLUMN_SIZE"));
        assertEquals(3, rs.getInt("DECIMAL_DIGITS"));

        assertFalse(rs.next());
    }

    @Test
    public void testPrimaryKeyMetadataScan() throws SQLException {
        long ts = nextTimestamp();
        ensureTableCreated(getUrl(), MDTEST_NAME, null, ts);
        ensureTableCreated(getUrl(), CUSTOM_ENTITY_DATA_FULL_NAME, null, ts);
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 5));
        Connection conn = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        DatabaseMetaData dbmd = conn.getMetaData();
        ResultSet rs;
        rs = dbmd.getPrimaryKeys(null, "", MDTEST_NAME);
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(MDTEST_NAME, rs.getString("TABLE_NAME"));
        assertEquals(null, rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("id"), rs.getString("COLUMN_NAME"));
        assertEquals(1, rs.getInt("KEY_SEQ"));
        assertEquals(null, rs.getString("PK_NAME"));
        assertFalse(rs.next());
        
        rs = dbmd.getPrimaryKeys(null, CUSTOM_ENTITY_DATA_SCHEMA_NAME, CUSTOM_ENTITY_DATA_NAME);
        assertTrue(rs.next());
        assertEquals(CUSTOM_ENTITY_DATA_SCHEMA_NAME, rs.getString("TABLE_SCHEM"));
        assertEquals(CUSTOM_ENTITY_DATA_NAME, rs.getString("TABLE_NAME"));
        assertEquals(null, rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("organization_id"), rs.getString("COLUMN_NAME"));
        assertEquals(1, rs.getInt("KEY_SEQ"));
        assertEquals(SchemaUtil.normalizeIdentifier("pk"), rs.getString("PK_NAME")); // TODO: this is on the table row
        
        assertTrue(rs.next());
        assertEquals(CUSTOM_ENTITY_DATA_SCHEMA_NAME, rs.getString("TABLE_SCHEM"));
        assertEquals(CUSTOM_ENTITY_DATA_NAME, rs.getString("TABLE_NAME"));
        assertEquals(null, rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("key_prefix"), rs.getString("COLUMN_NAME"));
        assertEquals(2, rs.getInt("KEY_SEQ"));
        assertEquals(SchemaUtil.normalizeIdentifier("pk"), rs.getString("PK_NAME"));
        
        assertTrue(rs.next());
        assertEquals(CUSTOM_ENTITY_DATA_SCHEMA_NAME, rs.getString("TABLE_SCHEM"));
        assertEquals(CUSTOM_ENTITY_DATA_NAME, rs.getString("TABLE_NAME"));
        assertEquals(null, rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("custom_entity_data_id"), rs.getString("COLUMN_NAME"));
        assertEquals(3, rs.getInt("KEY_SEQ"));
        assertEquals(SchemaUtil.normalizeIdentifier("pk"), rs.getString("PK_NAME"));

        assertFalse(rs.next());

        rs = dbmd.getColumns("", CUSTOM_ENTITY_DATA_SCHEMA_NAME, CUSTOM_ENTITY_DATA_NAME, null);
        assertTrue(rs.next());
        assertEquals(CUSTOM_ENTITY_DATA_SCHEMA_NAME, rs.getString("TABLE_SCHEM"));
        assertEquals(CUSTOM_ENTITY_DATA_NAME, rs.getString("TABLE_NAME"));
        assertEquals(null, rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("organization_id"), rs.getString("COLUMN_NAME"));
        assertEquals(rs.getInt("COLUMN_SIZE"), 15);
        
        assertTrue(rs.next());
        assertEquals(CUSTOM_ENTITY_DATA_SCHEMA_NAME, rs.getString("TABLE_SCHEM"));
        assertEquals(CUSTOM_ENTITY_DATA_NAME, rs.getString("TABLE_NAME"));
        assertEquals(null, rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("key_prefix"), rs.getString("COLUMN_NAME"));
        assertEquals(rs.getInt("COLUMN_SIZE"), 3);
        
        assertTrue(rs.next());
        assertEquals(CUSTOM_ENTITY_DATA_SCHEMA_NAME, rs.getString("TABLE_SCHEM"));
        assertEquals(CUSTOM_ENTITY_DATA_NAME, rs.getString("TABLE_NAME"));
        assertEquals(null, rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("custom_entity_data_id"), rs.getString("COLUMN_NAME"));
        
        // The above returns all columns, starting with the PK columns
        assertTrue(rs.next());
        
        rs = dbmd.getColumns("", CUSTOM_ENTITY_DATA_SCHEMA_NAME.toLowerCase(), CUSTOM_ENTITY_DATA_NAME.toLowerCase(), "key_prefix");
        assertTrue(rs.next());
        assertEquals(CUSTOM_ENTITY_DATA_SCHEMA_NAME, rs.getString("TABLE_SCHEM"));
        assertEquals(CUSTOM_ENTITY_DATA_NAME, rs.getString("TABLE_NAME"));
        assertEquals(null, rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("key_prefix"), rs.getString("COLUMN_NAME"));
        
        rs = dbmd.getColumns("", CUSTOM_ENTITY_DATA_SCHEMA_NAME.toLowerCase(), CUSTOM_ENTITY_DATA_NAME.toLowerCase(), "key_prefix".toUpperCase());
        assertTrue(rs.next());
        assertEquals(CUSTOM_ENTITY_DATA_SCHEMA_NAME, rs.getString("TABLE_SCHEM"));
        assertEquals(CUSTOM_ENTITY_DATA_NAME, rs.getString("TABLE_NAME"));
        assertEquals(null, rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("key_prefix"), rs.getString("COLUMN_NAME"));
        
        assertFalse(rs.next());
        
    }
    
    @Test
    public void testMultiTableColumnsMetadataScan() throws SQLException {
        long ts = nextTimestamp();
        ensureTableCreated(getUrl(), MDTEST_NAME, null, ts);
        ensureTableCreated(getUrl(), GROUPBYTEST_NAME, null, ts);
        ensureTableCreated(getUrl(), PTSDB_NAME, null, ts);
        ensureTableCreated(getUrl(), CUSTOM_ENTITY_DATA_FULL_NAME, null, ts);
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 5));
        Connection conn = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        DatabaseMetaData dbmd = conn.getMetaData();
        ResultSet rs = dbmd.getColumns(null, "", "%TEST%", null);
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(rs.getString("TABLE_NAME"),GROUPBYTEST_NAME);
        assertEquals(null, rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("id"), rs.getString("COLUMN_NAME"));
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(rs.getString("TABLE_NAME"),GROUPBYTEST_NAME);
        assertEquals(PhoenixDatabaseMetaData.TABLE_FAMILY, rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("uri"), rs.getString("COLUMN_NAME"));
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(rs.getString("TABLE_NAME"),GROUPBYTEST_NAME);
        assertEquals(PhoenixDatabaseMetaData.TABLE_FAMILY, rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("appcpu"), rs.getString("COLUMN_NAME"));
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(rs.getString("TABLE_NAME"),MDTEST_NAME);
        assertEquals(null, rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("id"), rs.getString("COLUMN_NAME"));
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(rs.getString("TABLE_NAME"),MDTEST_NAME);
        assertEquals(SchemaUtil.normalizeIdentifier("a"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col1"), rs.getString("COLUMN_NAME"));
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(rs.getString("TABLE_NAME"),MDTEST_NAME);
        assertEquals(SchemaUtil.normalizeIdentifier("b"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col2"), rs.getString("COLUMN_NAME"));
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(rs.getString("TABLE_NAME"),MDTEST_NAME);
        assertEquals(SchemaUtil.normalizeIdentifier("b"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col3"), rs.getString("COLUMN_NAME"));
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(rs.getString("TABLE_NAME"),MDTEST_NAME);
        assertEquals(SchemaUtil.normalizeIdentifier("b"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col4"), rs.getString("COLUMN_NAME"));
        assertTrue(rs.next());
        assertEquals(rs.getString("TABLE_SCHEM"),null);
        assertEquals(rs.getString("TABLE_NAME"),MDTEST_NAME);
        assertEquals(SchemaUtil.normalizeIdentifier("b"), rs.getString("TABLE_CAT"));
        assertEquals(SchemaUtil.normalizeIdentifier("col5"), rs.getString("COLUMN_NAME"));
        assertFalse(rs.next());
    }
    
    @Test
    public void testCreateDropTable() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        initATableValues(tenantId, getDefaultSplits(tenantId), null, ts);
        
        ensureTableCreated(getUrl(), BTABLE_NAME, null, ts-2);
        ensureTableCreated(getUrl(), PTSDB_NAME, null, ts-2);
        
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 5));
        Connection conn5 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        String query = "SELECT a_string FROM aTable";
        // Data should still be there b/c we only dropped the schema
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 8));
        assertTrue(conn5.prepareStatement(query).executeQuery().next());
        conn5.createStatement().executeUpdate("DROP TABLE " + ATABLE_NAME);
        
        // Confirm that data is no longer there because we dropped the table
        // This needs to be done natively b/c the metadata is gone
        HTableInterface htable = conn5.unwrap(PhoenixConnection.class).getQueryServices().getTable(SchemaUtil.getTableNameAsBytes(ATABLE_SCHEMA_NAME, ATABLE_NAME));
        Scan scan = new Scan();
        scan.setFilter(new FirstKeyOnlyFilter());
        scan.setTimeRange(0, ts+9);
        assertNull(htable.getScanner(scan).next());
        conn5.close();

        // Still should work b/c we're at an earlier timestamp than when table was deleted
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 2));
        Connection conn2 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        assertTrue(conn2.prepareStatement(query).executeQuery().next());
        conn2.close();
        
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 10));
        Connection conn10 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        try {
            conn10.prepareStatement(query).executeQuery().next();
            fail();
        } catch (TableNotFoundException e) {
        }
    }
 
    @Test
    public void testCreateOnExistingTable() throws Exception {
        PhoenixConnection pconn = DriverManager.getConnection(PHOENIX_JDBC_URL, TEST_PROPERTIES).unwrap(PhoenixConnection.class);
        String tableName = MDTEST_NAME;
        String schemaName = MDTEST_SCHEMA_NAME;
        byte[] cfA = Bytes.toBytes(SchemaUtil.normalizeIdentifier("a"));
        byte[] cfB = Bytes.toBytes(SchemaUtil.normalizeIdentifier("b"));
        byte[] cfC = Bytes.toBytes("c");
        byte[][] familyNames = new byte[][] {cfB, cfC};
        byte[] htableName = SchemaUtil.getTableNameAsBytes(schemaName, tableName);
        HBaseAdmin admin = pconn.getQueryServices().getAdmin();
        try {
            admin.disableTable(htableName);
            admin.deleteTable(htableName);
            admin.enableTable(htableName);
        } catch (org.apache.hadoop.hbase.TableNotFoundException e) {
        }
        
        HTableDescriptor descriptor = new HTableDescriptor(htableName);
        for (byte[] familyName : familyNames) {
            HColumnDescriptor columnDescriptor = new HColumnDescriptor(familyName);
            descriptor.addFamily(columnDescriptor);
        }
        admin.createTable(descriptor);
            
        long ts = nextTimestamp();
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 5));
        PhoenixConnection conn1 = DriverManager.getConnection(PHOENIX_JDBC_URL, props).unwrap(PhoenixConnection.class);
        ensureTableCreated(getUrl(), tableName, null, ts);
        
        descriptor = admin.getTableDescriptor(htableName);
        assertEquals(3,descriptor.getColumnFamilies().length);
        HColumnDescriptor cdA = descriptor.getFamily(cfA);
        assertTrue(cdA.getKeepDeletedCells());
        assertEquals(DataBlockEncoding.NONE, cdA.getDataBlockEncoding()); // Overriden using WITH
        assertEquals(1,cdA.getMaxVersions());// Overriden using WITH
        HColumnDescriptor cdB = descriptor.getFamily(cfB);
        assertFalse(cdB.getKeepDeletedCells()); // Allow KEEP_DELETED_CELLS to be false for VEIW
        assertEquals(DataBlockEncoding.NONE, cdB.getDataBlockEncoding()); // Should keep the original value.
        // CF c should stay the same since it's not a Phoenix cf.
        HColumnDescriptor cdC = descriptor.getFamily(cfC);
        assertNotNull("Column family not found", cdC);
        assertFalse(cdC.getKeepDeletedCells());
        assertFalse(SchemaUtil.DEFAULT_DATA_BLOCK_ENCODING == cdC.getDataBlockEncoding());
        assertTrue(descriptor.hasCoprocessor(UngroupedAggregateRegionObserver.class.getName()));
        assertTrue(descriptor.hasCoprocessor(GroupedAggregateRegionObserver.class.getName()));
        assertTrue(descriptor.hasCoprocessor(ServerCachingEndpointImpl.class.getName()));
        admin.close();
         
        int rowCount = 5;
        String upsert = "UPSERT INTO " + tableName + "(id,col1,col2) VALUES(?,?,?)";
        PreparedStatement ps = conn1.prepareStatement(upsert);
        for (int i = 0; i < rowCount; i++) {
            ps.setString(1, Integer.toString(i));
            ps.setInt(2, i+1);
            ps.setInt(3, i+2);
            ps.execute();
        }
        conn1.commit();
        conn1.close();
        
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 6));
        Connection conn2 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        String query = "SELECT count(1) FROM " + tableName;
        ResultSet rs = conn2.createStatement().executeQuery(query);
        assertTrue(rs.next());
        assertEquals(rowCount, rs.getLong(1));
        
        query = "SELECT id, col1,col2 FROM " + tableName;
        rs = conn2.createStatement().executeQuery(query);
        for (int i = 0; i < rowCount; i++) {
            assertTrue(rs.next());
            assertEquals(Integer.toString(i),rs.getString(1));
            assertEquals(i+1, rs.getInt(2));
            assertEquals(i+2, rs.getInt(3));
        }
        assertFalse(rs.next());
        conn2.close();
    }
    
    @Test
    public void testCreateViewOnExistingTable() throws Exception {
        PhoenixConnection pconn = DriverManager.getConnection(PHOENIX_JDBC_URL, TEST_PROPERTIES).unwrap(PhoenixConnection.class);
        String tableName = MDTEST_NAME;
        String schemaName = MDTEST_SCHEMA_NAME;
        byte[] cfB = Bytes.toBytes(SchemaUtil.normalizeIdentifier("b"));
        byte[] cfC = Bytes.toBytes("c");
        byte[][] familyNames = new byte[][] {cfB, cfC};
        byte[] htableName = SchemaUtil.getTableNameAsBytes(schemaName, tableName);
        HBaseAdmin admin = pconn.getQueryServices().getAdmin();
        try {
            admin.disableTable(htableName);
            admin.deleteTable(htableName);
            admin.enableTable(htableName);
        } catch (org.apache.hadoop.hbase.TableNotFoundException e) {
        } finally {
            admin.close();
        }
        
        HTableDescriptor descriptor = new HTableDescriptor(htableName);
        for (byte[] familyName : familyNames) {
            HColumnDescriptor columnDescriptor = new HColumnDescriptor(familyName);
            descriptor.addFamily(columnDescriptor);
        }
        admin.createTable(descriptor);
            
        long ts = nextTimestamp();
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 5));
        Connection conn1 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        String createStmt = "create view bogusTable" + 
        "   (id char(1) not null primary key,\n" + 
        "    a.col1 integer,\n" +
        "    d.col2 bigint)\n";
        try {
            conn1.createStatement().execute(createStmt);
            fail();
        } catch (TableNotFoundException e) {
            // expected to fail b/c table doesn't exist
        }
        createStmt = "create view " + MDTEST_NAME + 
                "   (id char(1) not null primary key,\n" + 
                "    a.col1 integer,\n" +
                "    b.col2 bigint)\n";
        try {
            conn1.createStatement().execute(createStmt);
            fail();
        } catch (ReadOnlyTableException e) {
            // expected to fail b/c cf a doesn't exist
        }
        createStmt = "create view " + MDTEST_NAME + 
        "   (id char(1) not null primary key,\n" + 
        "    b.col1 integer,\n" +
        "    c.col2 bigint)\n";
        try {
            conn1.createStatement().execute(createStmt);
            fail();
        } catch (ReadOnlyTableException e) {
            // expected to fail b/c cf C doesn't exist (case issue)
        }

        createStmt = "create view " + MDTEST_NAME + 
        "   (id char(1) not null primary key,\n" + 
        "    b.col1 integer,\n" +
        "    \"c\".col2 bigint) \n";
        // should be ok now
        conn1.createStatement().execute(createStmt);
        conn1.close();
                 
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 6));
        PhoenixConnection conn2 = DriverManager.getConnection(PHOENIX_JDBC_URL, props).unwrap(PhoenixConnection.class);
        
        ResultSet rs = conn2.getMetaData().getTables(null, null, MDTEST_NAME, null);
        assertTrue(rs.next());
        assertEquals(ViewType.MAPPED.name(), rs.getString(PhoenixDatabaseMetaData.VIEW_TYPE));
        assertFalse(rs.next());

        String deleteStmt = "DELETE FROM " + MDTEST_NAME;
        PreparedStatement ps = conn2.prepareStatement(deleteStmt);
        try {
            ps.execute();
            fail();
        } catch (ReadOnlyTableException e) {
            // expected to fail b/c table is read-only
        }
        try {
            String upsert = "UPSERT INTO " + MDTEST_NAME + "(id,col1,col2) VALUES(?,?,?)";
            ps = conn2.prepareStatement(upsert);
            try {
                ps.setString(1, Integer.toString(0));
                ps.setInt(2, 1);
                ps.setInt(3, 2);
                ps.execute();
                fail();
            } catch (ReadOnlyTableException e) {
                // expected to fail b/c table is read-only
            }
            conn2.createStatement().execute("ALTER VIEW " + MDTEST_NAME + " SET IMMUTABLE_ROWS=TRUE");
            
            HTableInterface htable = conn2.getQueryServices().getTable(SchemaUtil.getTableNameAsBytes(MDTEST_SCHEMA_NAME,MDTEST_NAME));
            Put put = new Put(Bytes.toBytes("0"));
            put.add(cfB, Bytes.toBytes("COL1"), ts+6, PDataType.INTEGER.toBytes(1));
            put.add(cfC, Bytes.toBytes("COL2"), ts+6, PDataType.LONG.toBytes(2));
            htable.put(put);
            conn2.close();
            
            props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 10));
            Connection conn7 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
            // Should be ok b/c we've marked the view with IMMUTABLE_ROWS=true
            conn7.createStatement().execute("CREATE INDEX idx ON " + MDTEST_NAME + "(B.COL1)");
            String select = "SELECT col1 FROM " + MDTEST_NAME + " WHERE col2=?";
            ps = conn7.prepareStatement(select);
            ps.setInt(1, 2);
            rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
            assertFalse(rs.next());
            
            props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 12));
            Connection conn75 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
            String dropTable = "DROP TABLE " + MDTEST_NAME ;
            ps = conn75.prepareStatement(dropTable);
            try {
                ps.execute();
                fail();
            } catch (TableNotFoundException e) {
                // expected to fail b/c it is a view
            }
    
            String dropView = "DROP VIEW " + MDTEST_NAME ;
            ps = conn75.prepareStatement(dropView);
            ps.execute();
            conn75.close();
            
            props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 15));
            Connection conn8 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
            createStmt = "create view " + MDTEST_NAME + 
                    "   (id char(1) not null primary key,\n" + 
                    "    b.col1 integer,\n" +
                    "    \"c\".col2 bigint) IMMUTABLE_ROWS=true\n";
            // should be ok to create a view with IMMUTABLE_ROWS = true
            conn8.createStatement().execute(createStmt);
            conn8.close();
            
            props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 20));
            Connection conn9 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
            conn9.createStatement().execute("CREATE INDEX idx ON " + MDTEST_NAME + "(B.COL1)");
            
            props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 30));
            Connection conn91 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
            ps = conn91.prepareStatement(dropView);
            ps.execute();
            conn91.close();
            
            props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 35));
            Connection conn92 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
            createStmt = "create view " + MDTEST_NAME + 
                    "   (id char(1) not null primary key,\n" + 
                    "    b.col1 integer,\n" +
                    "    \"c\".col2 bigint) as\n" +
                    " select * from " + MDTEST_NAME + 
                    " where b.col1 = 1";
            conn92.createStatement().execute(createStmt);
            conn92.close();
            
            put = new Put(Bytes.toBytes("1"));
            put.add(cfB, Bytes.toBytes("COL1"), ts+39, PDataType.INTEGER.toBytes(3));
            put.add(cfC, Bytes.toBytes("COL2"), ts+39, PDataType.LONG.toBytes(4));
            htable.put(put);

            props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 40));
            Connection conn92a = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
            rs = conn92a.createStatement().executeQuery("select count(*) from " + MDTEST_NAME);
            assertTrue(rs.next());
            assertEquals(1,rs.getInt(1));
            conn92a.close();

            props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 45));
            Connection conn93 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
            try {
                String alterView = "alter view " + MDTEST_NAME + " drop column b.col1";
                conn93.createStatement().execute(alterView);
                fail();
            } catch (SQLException e) {
                assertEquals(SQLExceptionCode.CANNOT_MUTATE_TABLE.getErrorCode(), e.getErrorCode());
            }
            conn93.close();
            
            props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 50));
            Connection conn94 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
            String alterView = "alter view " + MDTEST_NAME + " drop column \"c\".col2";
            conn94.createStatement().execute(alterView);
            conn94.close();
            
        } finally {
            HTableInterface htable = pconn.getQueryServices().getTable(SchemaUtil.getTableNameAsBytes(MDTEST_SCHEMA_NAME,MDTEST_NAME));
            Delete delete1 = new Delete(Bytes.toBytes("0"));
            Delete delete2 = new Delete(Bytes.toBytes("1"));
            htable.batch(Arrays.asList(delete1, delete2));
        }
        
    }
    
    @Test
    public void testAddKVColumnToExistingFamily() throws Throwable {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        initATableValues(tenantId, getDefaultSplits(tenantId), null, ts);
        
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 5));
        Connection conn1 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        // Failed attempt to repro table not found bug
//        TestUtil.clearMetaDataCache(conn1);
//        PhoenixConnection pconn = conn1.unwrap(PhoenixConnection.class);
//        pconn.removeTable(ATABLE_SCHEMA_NAME, ATABLE_NAME);
        conn1.createStatement().executeUpdate("ALTER TABLE " + ATABLE_NAME + " ADD z_integer integer");
        conn1.close();
 
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 6));
        Connection conn2 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        String query = "SELECT z_integer FROM aTable";
        assertTrue(conn2.prepareStatement(query).executeQuery().next());
        conn2.close();
        
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 3));
        Connection conn3 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        try {
            conn3.prepareStatement(query).executeQuery().next();
            fail();
        } catch (ColumnNotFoundException e) {
        }
    }
    
    @Test
    public void testAddKVColumnToNewFamily() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        initATableValues(tenantId, getDefaultSplits(tenantId), null, ts);
        
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 5));
        Connection conn1 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        conn1.createStatement().executeUpdate("ALTER TABLE " + ATABLE_NAME + " ADD newcf.z_integer integer");
        conn1.close();
 
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 6));
        Connection conn2 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        String query = "SELECT z_integer FROM aTable";
        assertTrue(conn2.prepareStatement(query).executeQuery().next());
        conn2.close();
        
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 3));
        Connection conn3 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        try {
            conn3.prepareStatement(query).executeQuery().next();
            fail();
        } catch (ColumnNotFoundException e) {
        }
    }
    
    @Test
    public void testAddPKColumn() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        initATableValues(tenantId, getDefaultSplits(tenantId), null, ts);
        
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 5));
        Connection conn1 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        try {
            conn1.createStatement().executeUpdate("ALTER TABLE " + ATABLE_NAME + " ADD z_string varchar not null primary key");
            fail();
        } catch (SQLException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("ERROR 1006 (42J04): Only nullable columns may be added to a multi-part row key."));
        }
        conn1.createStatement().executeUpdate("ALTER TABLE " + ATABLE_NAME + " ADD z_string varchar primary key");
        conn1.close();
 
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 6));
        Connection conn2 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        String query = "SELECT z_string FROM aTable";
        assertTrue(conn2.prepareStatement(query).executeQuery().next());
        conn2.close();
        
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 3));
        Connection conn3 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        try {
            conn3.prepareStatement(query).executeQuery().next();
            fail();
        } catch (ColumnNotFoundException e) {
        }
    }
    
    @Test
    public void testDropKVColumn() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        initATableValues(tenantId, getDefaultSplits(tenantId), null, ts);
        
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 5));
        Connection conn5 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        assertTrue(conn5.createStatement().executeQuery("SELECT 1 FROM atable WHERE b_string IS NOT NULL").next());
        conn5.createStatement().executeUpdate("ALTER TABLE " + ATABLE_NAME + " DROP COLUMN b_string");
        conn5.close();
 
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 6));
        Connection conn2 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        String query = "SELECT b_string FROM aTable";
        try {
            conn2.prepareStatement(query).executeQuery().next();
            fail();
        } catch (ColumnNotFoundException e) {
        }
        conn2.close();
        
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 3));
        Connection conn3 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        assertTrue(conn3.prepareStatement(query).executeQuery().next());
        conn3.close();

        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 7));
        Connection conn7 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        conn7.createStatement().executeUpdate("ALTER TABLE " + ATABLE_NAME + " ADD b_string VARCHAR");
        conn7.close();
    
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 8));
        Connection conn8 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        assertFalse(conn8.createStatement().executeQuery("SELECT 1 FROM atable WHERE b_string IS NOT NULL").next());
        conn8.close();
        
    }
    
    @Test
    public void testDropPKColumn() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        initATableValues(tenantId, getDefaultSplits(tenantId), null, ts);
        
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 5));
        Connection conn1 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        try {
            conn1.createStatement().executeUpdate("ALTER TABLE " + ATABLE_NAME + " DROP COLUMN entity_id");
            fail();
        } catch (SQLException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("ERROR 506 (42817): Primary key column may not be dropped."));
        }
        conn1.close();
    }

    @Test
    public void testDropAllKVCols() throws Exception {
        ResultSet rs;
        long ts = nextTimestamp();
        ensureTableCreated(getUrl(), MDTEST_NAME, null, ts);
        
        Properties props = new Properties();
        
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 2));
        Connection conn2 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        conn2.createStatement().executeUpdate("UPSERT INTO " + MDTEST_NAME + " VALUES('a',1,1)");
        conn2.createStatement().executeUpdate("UPSERT INTO " + MDTEST_NAME + " VALUES('b',2,2)");
        conn2.commit();
        conn2.close();

        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 3));
        Connection conn3 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        rs = conn3.createStatement().executeQuery("SELECT count(1) FROM " + MDTEST_NAME);
        assertTrue(rs.next());
        assertEquals(2, rs.getLong(1));
        conn3.close();
        
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 5));
        Connection conn5 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        conn5.createStatement().executeUpdate("ALTER TABLE " + MDTEST_NAME + " DROP COLUMN col1");
        conn5.close();
        
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 6));
        Connection conn6 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        rs = conn6.createStatement().executeQuery("SELECT count(1) FROM " + MDTEST_NAME);
        assertTrue(rs.next());
        assertEquals(2, rs.getLong(1));
        conn6.close();
        
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 7));
        Connection conn7 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        conn7.createStatement().executeUpdate("ALTER TABLE " + MDTEST_NAME + " DROP COLUMN col2");
        conn7.close();
        
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 8));
        Connection conn8 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        rs = conn8.createStatement().executeQuery("SELECT count(1) FROM " + MDTEST_NAME);
        assertTrue(rs.next());
        assertEquals(2, rs.getLong(1));
        conn8.close();
    }

    @Test
    public void testNewerTableDisallowed() throws Exception {
        long ts = nextTimestamp();
        ensureTableCreated(getUrl(), ATABLE_NAME, null, ts);
        
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts + 5));
        Connection conn5 = DriverManager.getConnection(PHOENIX_JDBC_URL, props);
        conn5.createStatement().executeUpdate("ALTER TABLE " + ATABLE_NAME + " DROP COLUMN x_integer");
        try {
            conn5.createStatement().executeUpdate("ALTER TABLE " + ATABLE_NAME + " DROP COLUMN y_integer");
            fail();
        } catch (SQLException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("ERROR 1013 (42M04): Table already exists. tableName=ATABLE"));
        }
        conn5.close();
    }
}
