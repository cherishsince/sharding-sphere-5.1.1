package org.apache.shardingsphere;

import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ShardingYamlDemo {

    public static void main(String[] args) throws SQLException, IOException {
        File dataSourceYamlFile = new File("/Users/sin/projects/github/sharding-sphere-5.1.1/jdbc-test/src/main/resources/data-source.yaml");
        DataSource dataSource = YamlShardingSphereDataSourceFactory.createDataSource(dataSourceYamlFile);
        Connection connection = dataSource.getConnection();
        PreparedStatement preparedStatement = connection.prepareStatement("select * from t_user_0");
        ResultSet resultSet = preparedStatement.executeQuery();
        while (resultSet.next()) {
            for (int i = 0; i < resultSet.getFetchSize(); i++) {
                System.err.println(resultSet.getString(i));
            }
        }
        connection.close();
    }
}
