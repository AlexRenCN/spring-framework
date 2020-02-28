/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.transaction;

import java.io.Flushable;

/**
 * 事务状态的表示。
 * Representation of the status of a transaction.
 *
 * 事务代码可以使用它来检索状态信息，并以编程方式请求回滚（而不是引发导致隐式回滚的异常）。
 * <p>Transactional code can use this to retrieve status information,
 * and to programmatically request a rollback (instead of throwing
 * an exception that causes an implicit rollback).
 *
 *
 * <p>Includes the {@link SavepointManager} interface to provide access
 * to savepoint management facilities. Note that savepoint management
 * is only available if supported by the underlying transaction manager.
 *
 * @author Juergen Hoeller
 * @since 27.03.2003
 * @see #setRollbackOnly()
 * @see PlatformTransactionManager#getTransaction
 * @see org.springframework.transaction.support.TransactionCallback#doInTransaction
 * @see org.springframework.transaction.interceptor.TransactionInterceptor#currentTransactionStatus()
 */
public interface TransactionStatus extends TransactionExecution, SavepointManager, Flushable {

	/**
	 * 返回此事务是否在内部携带保存点，即已创建为基于保存点的嵌套事务。
	 * Return whether this transaction internally carries a savepoint,
	 * that is, has been created as nested transaction based on a savepoint.
	 * <p>This method is mainly here for diagnostic purposes, alongside
	 * {@link #isNewTransaction()}. For programmatic handling of custom
	 * savepoints, use the operations provided by {@link SavepointManager}.
	 * @see #isNewTransaction()
	 * @see #createSavepoint()
	 * @see #rollbackToSavepoint(Object)
	 * @see #releaseSavepoint(Object)
	 */
	boolean hasSavepoint();

	/**
	 * 将底层会话刷新到数据存储（如果适用）：例如，所有受影响的Hibernate/JPA会话。
	 * Flush the underlying session to the datastore, if applicable:
	 * for example, all affected Hibernate/JPA sessions.
	 * 这实际上只是一个提示，如果底层的事务管理器没有flush概念，
	 * 则可能是no-op。刷新信号可能应用于主资源或事务同步，取决于底层资源。
	 * <p>This is effectively just a hint and may be a no-op if the underlying
	 * transaction manager does not have a flush concept. A flush signal may
	 * get applied to the primary resource or to transaction synchronizations,
	 * depending on the underlying resource.
	 */
	@Override
	void flush();

}
