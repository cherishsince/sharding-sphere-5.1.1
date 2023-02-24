package org.apache.shardingsphere;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.config.algorithm.ShardingSphereAlgorithmConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * java api 配置
 */
public class ShardingJdbcJavaAPI {

    public static void main(String[] args) throws SQLException {
         ShardingJdbcJavaAPI shardingJdbcJavaAPI = new ShardingJdbcJavaAPI();
        // 创建 sharding dataSource
        DataSource dataSource = shardingJdbcJavaAPI.createShardingDataSource();
        // 查询
        shardingJdbcJavaAPI.selectUserList(dataSource);
        // 插入
//        shardingJdbcJavaAPI.insertUser(dataSource);
    }

    private void insertUser(DataSource dataSource) {
        // 打开链接
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement("insert into t_user(id, name)values(11111, '小明')");
            boolean execute = preparedStatement.execute();
            if (execute) {
                System.err.println("执行成功!");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void selectUserList(DataSource dataSource) {
        // 打开链接
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            PreparedStatement preparedStatement = connection.prepareStatement("select * from t_user");
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                System.err.println(resultSet.getString(1) + " " + resultSet.getString(2));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public DataSource createShardingDataSource() throws SQLException {
        Properties dataSourceProperties = new Properties();
        dataSourceProperties.put("sql-show", "true");
        return ShardingSphereDataSourceFactory.createDataSource(
                createDataSource(), Collections.singleton(createShardingRuleConfiguration()), dataSourceProperties);
    }


    private ShardingRuleConfiguration createShardingRuleConfiguration() {
        // 创建 sharing 配置
        ShardingRuleConfiguration shardingRuleConfiguration = new ShardingRuleConfiguration();
        // 配置 t_user 规则
        shardingRuleConfiguration.getTables().add(new ShardingTableRuleConfiguration("t_user", "db_user_${1..2}.t_user_${0..1}"));
        // 配置 t_user 分库策略
        shardingRuleConfiguration.setDefaultDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("id", "my_mod"));
        // 配置 t_user 分表策略
        shardingRuleConfiguration.setDefaultTableShardingStrategy(new StandardShardingStrategyConfiguration("name", "my_hash_mod"));
        // 配置 sharding 分片算法
        Properties myModProperties = new Properties();
        myModProperties.put("sharding-count", 2);
        shardingRuleConfiguration.getShardingAlgorithms().put("my_mod", new ShardingSphereAlgorithmConfiguration("mod", myModProperties));

        Properties myHashModProperties = new Properties();
        myHashModProperties.put("sharding-count", 2);
        shardingRuleConfiguration.getShardingAlgorithms().put("my_hash_mod", new ShardingSphereAlgorithmConfiguration("hash_mod", myHashModProperties));
        // 配置 t_user 分布式主键
        shardingRuleConfiguration.getKeyGenerators().put("my_snowflake", new ShardingSphereAlgorithmConfiguration("SNOWFLAKE", new Properties()));
        return shardingRuleConfiguration;
    }


    private Map<String, DataSource> createDataSource() {
        HikariDataSource hikariDataSource1 = new HikariDataSource();
        hikariDataSource1.setDriverClassName("com.mysql.jdbc.Driver");
        hikariDataSource1.setJdbcUrl("jdbc:mysql://localhost:3310/db_user?useSSL=false");
        hikariDataSource1.setUsername("root");
        hikariDataSource1.setPassword("123456");

        HikariDataSource hikariDataSource2 = new HikariDataSource();
        hikariDataSource2.setDriverClassName("com.mysql.jdbc.Driver");
        hikariDataSource2.setJdbcUrl("jdbc:mysql://localhost:3311/db_user?useSSL=false");
        hikariDataSource2.setUsername("root");
        hikariDataSource2.setPassword("123456");

        HashMap<String, DataSource> dataSourceMap = new HashMap<>();
        dataSourceMap.put("db_user_1", hikariDataSource1);
        dataSourceMap.put("db_user_2", hikariDataSource2);
        return dataSourceMap;
    }
}
