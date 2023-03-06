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

package org.apache.shardingsphere.infra.executor.kernel;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.Getter;
import org.apache.shardingsphere.infra.exception.ShardingSphereException;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutionGroup;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutionGroupContext;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutorCallback;
import org.apache.shardingsphere.infra.executor.kernel.model.ExecutorDataMap;
import org.apache.shardingsphere.infra.executor.kernel.thread.ExecutorServiceManager;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Executor engine.
 */
@Getter
public final class ExecutorEngine implements AutoCloseable {
    
    private final ExecutorServiceManager executorServiceManager;
    
    public ExecutorEngine(final int executorSize) {
        executorServiceManager = new ExecutorServiceManager(executorSize);
    }
    
    /**
     * Execute.
     *
     * @param executionGroupContext execution group context
     * @param callback executor callback
     * @param <I> type of input value
     * @param <O> type of return value
     * @return execute result
     * @throws SQLException throw if execute failure
     */
    public <I, O> List<O> execute(final ExecutionGroupContext<I> executionGroupContext, final ExecutorCallback<I, O> callback) throws SQLException {
        return execute(executionGroupContext, null, callback, false);
    }
    
    /**
     * Execute.
     *
     * @param executionGroupContext execution group context
     * @param firstCallback first executor callback
     * @param callback other executor callback
     * @param serial whether using multi thread execute or not
     * @param <I> type of input value
     * @param <O> type of return value
     * @return execute result
     * @throws SQLException throw if execute failure
     */
    public <I, O> List<O> execute(final ExecutionGroupContext<I> executionGroupContext,
                                  final ExecutorCallback<I, O> firstCallback, final ExecutorCallback<I, O> callback, final boolean serial) throws SQLException {
        // <1> 如果执行组为空，代码没有执行单元，直接返回空列表
        if (executionGroupContext.getInputGroups().isEmpty()) {
            return Collections.emptyList();
        }
        // <2> 执行模式，串行执行 serialExecute、并行执行 parallelExecute
        return serial ? serialExecute(executionGroupContext.getInputGroups().iterator(), firstCallback, callback)
                : parallelExecute(executionGroupContext.getInputGroups().iterator(), firstCallback, callback);
    }
    
    private <I, O> List<O> serialExecute(final Iterator<ExecutionGroup<I>> executionGroups, final ExecutorCallback<I, O> firstCallback, final ExecutorCallback<I, O> callback) throws SQLException {
        // <1> 获取第一个执行但愿
        ExecutionGroup<I> firstInputs = executionGroups.next();
        // <2> 同步执行，回调 firstCallback 方法
        List<O> result = new LinkedList<>(syncExecute(firstInputs, null == firstCallback ? callback : firstCallback));
        while (executionGroups.hasNext()) {
            result.addAll(syncExecute(executionGroups.next(), callback));
        }
        return result;
    }
    
    private <I, O> List<O> parallelExecute(final Iterator<ExecutionGroup<I>> executionGroups, final ExecutorCallback<I, O> firstCallback, final ExecutorCallback<I, O> callback) throws SQLException {
        // <1> 获取第一个 ExecutionGroup(和串行执行一样)
        ExecutionGroup<I> firstInputs = executionGroups.next();
        // <2> 异步执行，返回 Future 进行回调
        Collection<ListenableFuture<Collection<O>>> restResultFutures = asyncExecute(executionGroups, callback);
        // <3> first 采用串行执行，executionGroups 剩余的采用并行执行
        return getGroupResults(syncExecute(firstInputs, null == firstCallback ? callback : firstCallback), restResultFutures);
    }
    
    private <I, O> Collection<O> syncExecute(final ExecutionGroup<I> executionGroup, final ExecutorCallback<I, O> callback) throws SQLException {
        return callback.execute(executionGroup.getInputs(), true, ExecutorDataMap.getValue());
    }
    
    private <I, O> Collection<ListenableFuture<Collection<O>>> asyncExecute(final Iterator<ExecutionGroup<I>> executionGroups, final ExecutorCallback<I, O> callback) {
        Collection<ListenableFuture<Collection<O>>> result = new LinkedList<>();
        while (executionGroups.hasNext()) {
            result.add(asyncExecute(executionGroups.next(), callback));
        }
        return result;
    }
    
    private <I, O> ListenableFuture<Collection<O>> asyncExecute(final ExecutionGroup<I> executionGroup, final ExecutorCallback<I, O> callback) {
        // <1>
        Map<String, Object> dataMap = ExecutorDataMap.getValue();
        // <2> ExecutorService 线程池，提交任务查询
        return executorServiceManager.getExecutorService().submit(() -> callback.execute(executionGroup.getInputs(), false, dataMap));
    }
    
    private <O> List<O> getGroupResults(final Collection<O> firstResults, final Collection<ListenableFuture<Collection<O>>> restFutures) throws SQLException {
        // <1> 将 first 返回结果放入 result
        List<O> result = new LinkedList<>(firstResults);
        // <2> 回调 Future 方法获取数据
        for (ListenableFuture<Collection<O>> each : restFutures) {
            try {
                result.addAll(each.get());
            } catch (final InterruptedException | ExecutionException ex) {
                return throwException(ex);
            }
        }
        return result;
    }
    
    private <O> List<O> throwException(final Exception exception) throws SQLException {
        if (exception.getCause() instanceof SQLException) {
            throw (SQLException) exception.getCause();
        }
        throw new ShardingSphereException(exception);
    }
    
    @Override
    public void close() {
        executorServiceManager.close();
    }
}
