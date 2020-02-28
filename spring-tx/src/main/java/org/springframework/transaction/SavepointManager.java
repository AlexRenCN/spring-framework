/*
 * Copyright 2002-2014 the original author or authors.
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

/**
 * 指定一个API以通用方式以编程方式管理事务保存点的接口。
 * 为TransactionStatus扩展为公开特定事务的保存点管理功能。
 * Interface that specifies an API to programmatically manage transaction
 * savepoints in a generic fashion. Extended by TransactionStatus to
 * expose savepoint management functionality for a specific transaction.
 *
 * <p>Note that savepoints can only work within an active transaction.
 * Just use this programmatic savepoint handling for advanced needs;
 * else, a subtransaction with PROPAGATION_NESTED is preferable.
 *
 * <p>This interface is inspired by JDBC 3.0's Savepoint mechanism
 * but is independent from any specific persistence technology.
 *
 * @author Juergen Hoeller
 * @since 1.1
 * @see TransactionStatus
 * @see TransactionDefinition#PROPAGATION_NESTED
 * @see java.sql.Savepoint
 */
public interface SavepointManager {

	/**
	 * 创建新的保存点。您可以通过rollbackToSavepoint回滚到特定的保存点,
	 * 并通过release savepoint显式释放不再需要的保存点。
	 * Create a new savepoint. You can roll back to a specific savepoint
	 * via {@code rollbackToSavepoint}, and explicitly release a savepoint
	 * that you don't need anymore via {@code releaseSavepoint}.
	 * <p>Note that most transaction managers will automatically release
	 * savepoints at transaction completion.
	 * @return a savepoint object, to be passed into
	 * {@link #rollbackToSavepoint} or {@link #releaseSavepoint}
	 * @throws NestedTransactionNotSupportedException if the underlying
	 * transaction does not support savepoints
	 * @throws TransactionException if the savepoint could not be created,
	 * for example because the transaction is not in an appropriate state
	 * @see java.sql.Connection#setSavepoint
	 */
	Object createSavepoint() throws TransactionException;

	/**
	 * 回滚到给定的保存点。
	 * Roll back to the given savepoint.
	 * 保存点将在之后自动释放。您可以显式调用{@link#releaseSavepoint（Object）}或依赖于事务完成时的自动释放。
	 * <p>The savepoint will <i>not</i> be automatically released afterwards.
	 * You may explicitly call {@link #releaseSavepoint(Object)} or rely on
	 * automatic release on transaction completion.
	 * @param savepoint the savepoint to roll back to
	 * @throws NestedTransactionNotSupportedException if the underlying
	 * transaction does not support savepoints
	 * @throws TransactionException if the rollback failed
	 * @see java.sql.Connection#rollback(java.sql.Savepoint)
	 */
	void rollbackToSavepoint(Object savepoint) throws TransactionException;

	/**
	 * 显式释放给定的保存点。
	 * Explicitly release the given savepoint.
	 * 请注意，大多数事务管理器将在事务完成时自动释放保存点。
	 * <p>Note that most transaction managers will automatically release
	 * savepoints on transaction completion.
	 * 如果正确的资源清理最终将在事务完成时发生，则实现应尽可能无提示地失败。
	 * <p>Implementations should fail as silently as possible if proper
	 * resource cleanup will eventually happen at transaction completion.
	 * @param savepoint the savepoint to release
	 * @throws NestedTransactionNotSupportedException if the underlying
	 * transaction does not support savepoints
	 * @throws TransactionException if the release failed
	 * @see java.sql.Connection#releaseSavepoint
	 */
	void releaseSavepoint(Object savepoint) throws TransactionException;

}
