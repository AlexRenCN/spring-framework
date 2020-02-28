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

import org.springframework.lang.Nullable;

/**
 * 这是spring基础事务管理器的中心接口
 * This is the central interface in Spring's transaction infrastructure.
 * 应用程序可以直接使用它，但它主要不是指API：
 * Applications can use this directly, but it is not primarily meant as API:
 * 通常，应用程序将使用事务运算符或通过AOP进行的声明性事务划分。
 * Typically, applications will work with either TransactionTemplate or
 * declarative transaction demarcation through AOP.
 *
 * 对于实现接口，建议继承AbstractPlatformTransactionManager，
 * 该类预先实现了定义的传播行为，并负责多线程事务同步管理对象里的事务处理。
 * 子类必须为底层事务的特定状态实现模板方法，
 * 例如：begin、suspend、resume、commit。
 * <p>For implementors, it is recommended to derive from the provided
 * {@link org.springframework.transaction.support.AbstractPlatformTransactionManager}
 * class, which pre-implements the defined propagation behavior and takes care
 * of transaction synchronization handling. Subclasses have to implement
 * template methods for specific states of the underlying transaction,
 * for example: begin, suspend, resume, commit.
 *
 * 此策略接口的默认实现是JtaTransactionManager和DataSourceTransactionManager，它们可以作为其他事务策略的实现指南。
 * <p>The default implementations of this strategy interface are
 * {@link org.springframework.transaction.jta.JtaTransactionManager} and
 * {@link org.springframework.jdbc.datasource.DataSourceTransactionManager},
 * which can serve as an implementation guide for other transaction strategies.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 16.05.2003
 * @see org.springframework.transaction.support.TransactionTemplate
 * @see org.springframework.transaction.interceptor.TransactionInterceptor
 */
public interface PlatformTransactionManager extends TransactionManager {

	/**
	 * 根据指定的传播行为返回当前活动事务或创建新事务。
	 * Return a currently active transaction or create a new one, according to
	 * the specified propagation behavior.
	 * 请注意，隔离级别或超时等参数将只应用于新事务，因此在参与活动事务时将被忽略。
	 * <p>Note that parameters like isolation level or timeout will only be applied
	 * to new transactions, and thus be ignored when participating in active ones.
	 * 此外，并非每个事务管理器都支持所有事务定义设置：当遇到不支持的设置时，正确的事务管理器实现应引发异常。
	 * <p>Furthermore, not all transaction definition settings will be supported
	 * by every transaction manager: A proper transaction manager implementation
	 * should throw an exception when unsupported settings are encountered.
	 * 上述规则的一个例外是只读标志，如果不支持显式只读模式，则应忽略该标志。实际上，只读标志只是潜在优化的提示。
	 * <p>An exception to the above rule is the read-only flag, which should be
	 * ignored if no explicit read-only mode is supported. Essentially, the
	 * read-only flag is just a hint for potential optimization.
	 * @param definition the TransactionDefinition instance (can be {@code null} for defaults),
	 * describing propagation behavior, isolation level, timeout etc.
	 * @return transaction status object representing the new or current transaction
	 * @throws TransactionException in case of lookup, creation, or system errors
	 * @throws IllegalTransactionStateException if the given transaction definition
	 * cannot be executed (for example, if a currently active transaction is in
	 * conflict with the specified propagation behavior)
	 * @see TransactionDefinition#getPropagationBehavior
	 * @see TransactionDefinition#getIsolationLevel
	 * @see TransactionDefinition#getTimeout
	 * @see TransactionDefinition#isReadOnly
	 */
	TransactionStatus getTransaction(@Nullable TransactionDefinition definition)
			throws TransactionException;

	/**
	 * 就给定事务的状态提交该事务。如果事务仅以编程方式标记为回滚，则执行回滚。
	 * Commit the given transaction, with regard to its status. If the transaction
	 * has been marked rollback-only programmatically, perform a rollback.
	 * 如果事务不是新事务，则省略提交以正确参与周围的事务。如果前一个事务已被挂起以用来创建新事务，
	 * 请在提交新事务后继续前一个事务。
	 * <p>If the transaction wasn't a new one, omit the commit for proper
	 * participation in the surrounding transaction. If a previous transaction
	 * has been suspended to be able to create a new one, resume the previous
	 * transaction after committing the new one.
	 * 请注意，当提交调用完成时，无论是正常还是引发异常，事务都必须完全完成并清除。
	 * 在这种情况下，回滚不应该被调用。
	 * <p>Note that when the commit call completes, no matter if normally or
	 * throwing an exception, the transaction must be fully completed and
	 * cleaned up. No rollback call should be expected in such a case.
	 * 如果此方法引发TransactionException以外的异常，则导致提交尝试失败。
	 * 例如，一个O/R映射工具可能在提交之前尝试刷新对数据库的更改，结果是DataAccessException导致事务失败。
	 * 在这种情况下，原始异常将传播到此提交方法的调用方。
	 * <p>If this method throws an exception other than a TransactionException,
	 * then some before-commit error caused the commit attempt to fail. For
	 * example, an O/R Mapping tool might have tried to flush changes to the
	 * database right before commit, with the resulting DataAccessException
	 * causing the transaction to fail. The original exception will be
	 * propagated to the caller of this commit method in such a case.
	 * @param status object returned by the {@code getTransaction} method
	 * @throws UnexpectedRollbackException in case of an unexpected rollback
	 * that the transaction coordinator initiated
	 * @throws HeuristicCompletionException in case of a transaction failure
	 * caused by a heuristic decision on the side of the transaction coordinator
	 * @throws TransactionSystemException in case of commit or system errors
	 * (typically caused by fundamental resource failures)
	 * @throws IllegalTransactionStateException if the given transaction
	 * is already completed (that is, committed or rolled back)
	 * @see TransactionStatus#setRollbackOnly
	 */
	void commit(TransactionStatus status) throws TransactionException;

	/**
	 * 执行给定事务的回滚。
	 * Perform a rollback of the given transaction.
	 * 如果事务不是新事务，只需将其设置为rollback，以便正确地参与周围的事务。
	 * 如果上一个事务已被挂起，并创建新事务，请在回滚新事务后继续上一个事务。
	 * <p>If the transaction wasn't a new one, just set it rollback-only for proper
	 * participation in the surrounding transaction. If a previous transaction
	 * has been suspended to be able to create a new one, resume the previous
	 * transaction after rolling back the new one.
	 * 如果commit引发异常，则不要对事务调用rollback。
	 * 即使在commit异常的情况下，当commit返回时，事务也将已完成并清理。
	 * 因此，提交失败后的回滚调用将导致IllegalTransactionStateException。
	 * <p><b>Do not call rollback on a transaction if commit threw an exception.</b>
	 * The transaction will already have been completed and cleaned up when commit
	 * returns, even in case of a commit exception. Consequently, a rollback call
	 * after commit failure will lead to an IllegalTransactionStateException.
	 * @param status object returned by the {@code getTransaction} method
	 * @throws TransactionSystemException in case of rollback or system errors
	 * (typically caused by fundamental resource failures)
	 * @throws IllegalTransactionStateException if the given transaction
	 * is already completed (that is, committed or rolled back)
	 */
	void rollback(TransactionStatus status) throws TransactionException;

}
