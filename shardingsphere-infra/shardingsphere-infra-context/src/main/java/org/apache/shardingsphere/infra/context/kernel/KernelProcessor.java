/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.infra.context.kernel;

import org.apache.shardingsphere.infra.binder.LogicSQL;
import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.config.props.ConfigurationPropertyKey;
import org.apache.shardingsphere.infra.executor.sql.context.ExecutionContext;
import org.apache.shardingsphere.infra.executor.sql.context.ExecutionContextBuilder;
import org.apache.shardingsphere.infra.executor.sql.log.SQLLogger;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.rewrite.SQLRewriteEntry;
import org.apache.shardingsphere.infra.rewrite.engine.result.SQLRewriteResult;
import org.apache.shardingsphere.infra.route.context.RouteContext;
import org.apache.shardingsphere.infra.route.engine.SQLRouteEngine;

/**
 * Kernel processor.
 */
public final class KernelProcessor {
    
    /**
     * Generate execution context.
     *
     * @param logicSQL logic SQL
     * @param metaData ShardingSphere meta data
     * @param props configuration properties
     * @return execution context
     */
    public ExecutionContext generateExecutionContext(final LogicSQL logicSQL, final ShardingSphereMetaData metaData, final ConfigurationProperties props) {
        // 1、根据logicSQL 路由
        RouteContext routeContext = route(logicSQL, metaData, props);
        // 2、重写sql
        SQLRewriteResult rewriteResult = rewrite(logicSQL, metaData, props, routeContext);
        // 3、创建执行上下文
        ExecutionContext result = createExecutionContext(logicSQL, metaData, routeContext, rewriteResult);
        // 4、sql日志输出(sql-show 参数)
        logSQL(logicSQL, props, result);
        return result;
    }
    
    private RouteContext route(final LogicSQL logicSQL, final ShardingSphereMetaData metaData, final ConfigurationProperties props) {
        // 重点：根据 logicSQL 路由到对应的 database、table 上
        // 在 ShardingSphere 叫做执行单元(ExecutionUnit) 就是 db1.table1、db1.table2、db2...
        return new SQLRouteEngine(metaData.getRuleMetaData().getRules(), props).route(logicSQL, metaData);
    }
    
    private SQLRewriteResult rewrite(final LogicSQL logicSQL, final ShardingSphereMetaData metaData, final ConfigurationProperties props, final RouteContext routeContext) {
        SQLRewriteEntry sqlRewriteEntry = new SQLRewriteEntry(metaData.getName(), metaData.getDefaultSchema(), props, metaData.getRuleMetaData().getRules());
        return sqlRewriteEntry.rewrite(logicSQL.getSql(), logicSQL.getParameters(), logicSQL.getSqlStatementContext(), routeContext);
    }
    
    private ExecutionContext createExecutionContext(final LogicSQL logicSQL, final ShardingSphereMetaData metaData, final RouteContext routeContext, final SQLRewriteResult rewriteResult) {
        return new ExecutionContext(logicSQL, ExecutionContextBuilder.build(metaData, rewriteResult, logicSQL.getSqlStatementContext()), routeContext);
    }
    
    private void logSQL(final LogicSQL logicSQL, final ConfigurationProperties props, final ExecutionContext executionContext) {
        if (props.<Boolean>getValue(ConfigurationPropertyKey.SQL_SHOW)) {
            SQLLogger.logSQL(logicSQL, props.<Boolean>getValue(ConfigurationPropertyKey.SQL_SIMPLE), executionContext);
        }
    }
}
