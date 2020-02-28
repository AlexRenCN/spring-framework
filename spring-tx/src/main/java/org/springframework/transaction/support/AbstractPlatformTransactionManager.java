/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.transaction.support;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.List;

import com.sun.xml.internal.bind.v2.TODO;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.Constants;
import org.springframework.lang.Nullable;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.InvalidTimeoutException;
import org.springframework.transaction.NestedTransactionNotSupportedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSuspensionNotSupportedException;
import org.springframework.transaction.UnexpectedRollbackException;

/**
 * 实现Spring标准事务工作流的抽象基类，作为具体平台事务管理器的基础，如JtaTransactionManager
 * Abstract base class that implements Spring's standard transaction workflow,
 * serving as basis for concrete platform transaction managers like
 * {@link org.springframework.transaction.jta.JtaTransactionManager}.
 *
 * 此基类提供以下工作流处理：
 * <p>This base class provides the following workflow handling:
 * <ul>
 * 确定是否存在现有事务；
 * <li>determines if there is an existing transaction;
 * 应用适当的传播行为；
 * <li>applies the appropriate propagation behavior;
 * 必要时暂停和恢复事务；
 * <li>suspends and resumes transactions if necessary;
 * 在提交时检查仅回滚标志；
 * <li>checks the rollback-only flag on commit;
 * 对回滚应用适当的修改（仅限实际回滚或设置回滚）；
 * <li>applies the appropriate modification on rollback
 * (actual rollback or setting rollback-only);
 * 触发注册的同步回调（如果多线程事务同步管理对象里的事务处于活动状态）。
 * <li>triggers registered synchronization callbacks
 * (if transaction synchronization is active).
 * </ul>
 *
 * 子类必须为事务的特定状态实现特定的模板方法，
 * 例如：begin、suspend、resume、commit、rollback。
 * 其中最重要方法都是抽象的，必须由一个具体的实现提供；
 * 对于其余的方法，则提供默认实现，因此重写是可选的。
 * <p>Subclasses have to implement specific template methods for specific
 * states of a transaction, e.g.: begin, suspend, resume, commit, rollback.
 * The most important of them are abstract and must be provided by a concrete
 * implementation; for the rest, defaults are provided, so overriding is optional.
 *
 * 多线程事务同步管理是用于注册在事务完成时调用的回调的通用机制。在JTA事务中运行时，
 * JDBC、Hibernate、JPA等的数据访问支持类主要在内部使用：
 * 它们注册在事务中打开资源，以便在事务完成时关闭，从而允许在事务中重用相同的Hibernate会话。
 * 同样的机制也可以用于应用程序中的自定义同步需求。
 * <p>Transaction synchronization is a generic mechanism for registering callbacks
 * that get invoked at transaction completion time. This is mainly used internally
 * by the data access support classes for JDBC, Hibernate, JPA, etc when running
 * within a JTA transaction: They register resources that are opened within the
 * transaction for closing at transaction completion time, allowing e.g. for reuse
 * of the same Hibernate Session within the transaction. The same mechanism can
 * also be leveraged for custom synchronization needs in an application.
 *
 * 这个类的状态是序列化的，允许序列化事务策略以及带有事务拦截器的代理。
 * 如果子类希望它们的状态也可序列化，那就取决于子类了。
 * 在这种情况下，它们应该实现{@code java.io.Serializable}标记接口，如果需要恢复任何临时状态，
 * 则可能实现私有的{@code readObject（）}方法（根据java序列化规则）。
 * <p>The state of this class is serializable, to allow for serializing the
 * transaction strategy along with proxies that carry a transaction interceptor.
 * It is up to subclasses if they wish to make their state to be serializable too.
 * They should implement the {@code java.io.Serializable} marker interface in
 * that case, and potentially a private {@code readObject()} method (according
 * to Java serialization rules) if they need to restore any transient state.
 *
 * @author Juergen Hoeller
 * @since 28.03.2003
 * @see #setTransactionSynchronization
 * @see TransactionSynchronizationManager
 * @see org.springframework.transaction.jta.JtaTransactionManager
 */
@SuppressWarnings("serial")
public abstract class AbstractPlatformTransactionManager implements PlatformTransactionManager, Serializable {

	/**
	 * 始终激活事务同步管理，即使对于“空”事务也是如此，该事务是由于不支持任何现有后端事务的传播而导致的。
	 * Always activate transaction synchronization, even for "empty" transactions
	 * that result from PROPAGATION_SUPPORTS with no existing backend transaction.
	 * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_SUPPORTS
	 * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_NOT_SUPPORTED
	 * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_NEVER
	 */
	public static final int SYNCHRONIZATION_ALWAYS = 0;

	/**
	 * 只为实际事务激活多线程事务同步管理，即不为传播支持的空事务激活事务同步管理，不存在后端事务。
	 * Activate transaction synchronization only for actual transactions,
	 * that is, not for empty ones that result from PROPAGATION_SUPPORTS with
	 * no existing backend transaction.
	 * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_REQUIRED
	 * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_MANDATORY
	 * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_REQUIRES_NEW
	 */
	public static final int SYNCHRONIZATION_ON_ACTUAL_TRANSACTION = 1;

	/**
	 * 从不进行活动多线程事务同步管理，即使对于实际事务也是如此。
	 * Never active transaction synchronization, not even for actual transactions.
	 */
	public static final int SYNCHRONIZATION_NEVER = 2;


	/**
	 * AbstractPlatformTransactionManager的常量实例
	 * Constants instance for AbstractPlatformTransactionManager.
	 */
	private static final Constants constants = new Constants(AbstractPlatformTransactionManager.class);


	protected transient Log logger = LogFactory.getLog(getClass());

	/**
	 * 事务同步管理，默认全部开启，包括空事务
	 */
	private int transactionSynchronization = SYNCHRONIZATION_ALWAYS;

	/**
	 * 默认超时时间
	 */
	private int defaultTimeout = TransactionDefinition.TIMEOUT_DEFAULT;

	/**
	 * 允许嵌套事务
	 */
	private boolean nestedTransactionAllowed = false;

	/**
	 * 验证现有事务
	 */
	private boolean validateExistingTransaction = false;

	/**
	 * 参与事务失败时的全局事务回滚
	 */
	private boolean globalRollbackOnParticipationFailure = true;

	/**
	 * 仅在全局事务回滚时异常
	 */
	private boolean failEarlyOnGlobalRollbackOnly = false;

	/**
	 * 提交失败时回滚
	 */
	private boolean rollbackOnCommitFailure = false;


	/**
	 * 通过该类中相应常量的名称设置事务同步多线程事务同步管理,例如参数为SYNCHRONIZATION_ALWAYS，使用这个类的静态变量
	 * Set the transaction synchronization by the name of the corresponding constant
	 * in this class, e.g. "SYNCHRONIZATION_ALWAYS".
	 * @param constantName name of the constant
	 * @see #SYNCHRONIZATION_ALWAYS
	 */
	public final void setTransactionSynchronizationName(String constantName) {
		setTransactionSynchronization(constants.asNumber(constantName).intValue());
	}

	/**
	 * 设置此事务管理器应激活线程绑定多线程事务同步管理支持。默认为“始终”。
	 * Set when this transaction manager should activate the thread-bound
	 * transaction synchronization support. Default is "always".
	 * <p>Note that transaction synchronization isn't supported for
	 * multiple concurrent transactions by different transaction managers.
	 * Only one transaction manager is allowed to activate it at any time.
	 * @see #SYNCHRONIZATION_ALWAYS
	 * @see #SYNCHRONIZATION_ON_ACTUAL_TRANSACTION
	 * @see #SYNCHRONIZATION_NEVER
	 * @see TransactionSynchronizationManager
	 * @see TransactionSynchronization
	 */
	public final void setTransactionSynchronization(int transactionSynchronization) {
		this.transactionSynchronization = transactionSynchronization;
	}

	/**
	 * 如果此事务管理器应激活线程绑定多线程事务同步管理支持，则返回。
	 * Return if this transaction manager should activate the thread-bound
	 * transaction synchronization support.
	 */
	public final int getTransactionSynchronization() {
		return this.transactionSynchronization;
	}

	/**
	 * 如果在事务级别没有指定超时，指定此事务管理器应应用的默认超时，以秒为单位。
	 * Specify the default timeout that this transaction manager should apply
	 * if there is no timeout specified at the transaction level, in seconds.
	 * 默认是底层事务基础结构的默认超时，例如，对于JTA提供程序，通常为30秒，
	 * <p>Default is the underlying transaction infrastructure's default timeout,
	 * e.g. typically 30 seconds in case of a JTA provider, indicated by the
	 * {@code TransactionDefinition.TIMEOUT_DEFAULT} value.
	 * @see org.springframework.transaction.TransactionDefinition#TIMEOUT_DEFAULT
	 */
	public final void setDefaultTimeout(int defaultTimeout) {
		if (defaultTimeout < TransactionDefinition.TIMEOUT_DEFAULT) {
			throw new InvalidTimeoutException("Invalid default timeout", defaultTimeout);
		}
		this.defaultTimeout = defaultTimeout;
	}

	/**
	 * 如果在事务级别没有指定超时，返回此事务管理器应应用的默认超时，以秒为单位。
	 * Return the default timeout that this transaction manager should apply
	 * if there is no timeout specified at the transaction level, in seconds.
	 * <p>Returns {@code TransactionDefinition.TIMEOUT_DEFAULT} to indicate
	 * the underlying transaction infrastructure's default timeout.
	 */
	public final int getDefaultTimeout() {
		return this.defaultTimeout;
	}

	/**
	 * 设置是否允许嵌套事务。默认值为“false”。
	 * Set whether nested transactions are allowed. Default is "false".
	 * <p>Typically initialized with an appropriate default by the
	 * concrete transaction manager subclass.
	 */
	public final void setNestedTransactionAllowed(boolean nestedTransactionAllowed) {
		this.nestedTransactionAllowed = nestedTransactionAllowed;
	}

	/**
	 * 返回是否允许嵌套事务。
	 * Return whether nested transactions are allowed.
	 */
	public final boolean isNestedTransactionAllowed() {
		return this.nestedTransactionAllowed;
	}

	/**
	 * 设置在参与事务之前是否应验证现有事务。
	 * Set whether existing transactions should be validated before participating
	 * in them.
	 * 当参与现有事务时(例如，遇到现有事务时，使用PROPAGATION_REQUIRED或PROPAGATION_SUPPORTS)，
	 * 外部事务的特征甚至可以应用于内部事务范围。验证将在内部事务定义上检测不兼容的隔离级别和只读设置，
	 * 并通过抛出相应的异常拒绝参与。
	 * <p>When participating in an existing transaction (e.g. with
	 * PROPAGATION_REQUIRED or PROPAGATION_SUPPORTS encountering an existing
	 * transaction), this outer transaction's characteristics will apply even
	 * to the inner transaction scope. Validation will detect incompatible
	 * isolation level and read-only settings on the inner transaction definition
	 * and reject participation accordingly through throwing a corresponding exception.
	 * 默认值为“false”，可以忽略内部事务设置，
	 * 简单地用外部事务的特征覆盖它们。
	 * 将此标记切换为“true”以执行严格的验证。
	 * <p>Default is "false", leniently ignoring inner transaction settings,
	 * simply overriding them with the outer transaction's characteristics.
	 * Switch this flag to "true" in order to enforce strict validation.
	 * @since 2.5.1
	 */
	public final void setValidateExistingTransaction(boolean validateExistingTransaction) {
		this.validateExistingTransaction = validateExistingTransaction;
	}

	/**
	 * 返回在参与事务之前是否应验证现有事务。
	 * Return whether existing transactions should be validated before participating
	 * in them.
	 * @since 2.5.1
	 */
	public final boolean isValidateExistingTransaction() {
		return this.validateExistingTransaction;
	}

	/**
	 * 设置是否在参与事务失败后将现有事务全局标记为仅回滚。
	 * Set whether to globally mark an existing transaction as rollback-only
	 * after a participating transaction failed.
	 * 默认值为“true”：如果参与事务（例如，需要PROPAGATION_或PROPAGATION_支持遇到现有事务）失败，
	 * 则该事务将被全局标记为仅回滚。此类事务的唯一可能结果是回滚：事务发起方不能再使事务提交。
	 * <p>Default is "true": If a participating transaction (e.g. with
	 * PROPAGATION_REQUIRED or PROPAGATION_SUPPORTS encountering an existing
	 * transaction) fails, the transaction will be globally marked as rollback-only.
	 * The only possible outcome of such a transaction is a rollback: The
	 * transaction originator <i>cannot</i> make the transaction commit anymore.
	 * 将此项切换为“false”，让事务发起方做出回滚决定。如果参与事务因异常而失败，
	 * 调用方仍然可以决定在事务中继续使用不同的路径。
	 * 但是，请注意，只有当所有参与资源即使在数据访问失败后仍能够继续进行事务提交时，
	 * 此操作才有效：例如，对于Hibernate会话，通常情况下不是这样；对于一系列JDBC插入/更新/删除操作，也不是这样。
	 * <p>Switch this to "false" to let the transaction originator make the rollback
	 * decision. If a participating transaction fails with an exception, the caller
	 * can still decide to continue with a different path within the transaction.
	 * However, note that this will only work as long as all participating resources
	 * are capable of continuing towards a transaction commit even after a data access
	 * failure: This is generally not the case for a Hibernate Session, for example;
	 * neither is it for a sequence of JDBC insert/update/delete operations.
	 * 此标志仅适用于子事务的显式回滚尝试，通常由数据访问操作引发的异常引起。
	 * 如果标志是关闭的，调用者可以处理异常并决定回滚，这与子事务的回滚规则无关。这个标志,
	 * 但是，不应用于对{@code TransactionStatus}的显式{@code setRollbackOnly}调用，这将始终导致最终的全局回滚(因为它可能不会在只回滚调用之后抛出异常)。
	 * <p><b>Note:</b>This flag only applies to an explicit rollback attempt for a
	 * subtransaction, typically caused by an exception thrown by a data access operation
	 * (where TransactionInterceptor will trigger a {@code PlatformTransactionManager.rollback()}
	 * call according to a rollback rule). If the flag is off, the caller can handle the exception
	 * and decide on a rollback, independent of the rollback rules of the subtransaction.
	 * This flag does, however, <i>not</i> apply to explicit {@code setRollbackOnly}
	 * calls on a {@code TransactionStatus}, which will always cause an eventual
	 * global rollback (as it might not throw an exception after the rollback-only call).
	 * <p>The recommended solution for handling failure of a subtransaction
	 * is a "nested transaction", where the global transaction can be rolled
	 * back to a savepoint taken at the beginning of the subtransaction.
	 * PROPAGATION_NESTED provides exactly those semantics; however, it will
	 * only work when nested transaction support is available. This is the case
	 * with DataSourceTransactionManager, but not with JtaTransactionManager.
	 * @see #setNestedTransactionAllowed
	 * @see org.springframework.transaction.jta.JtaTransactionManager
	 */
	public final void setGlobalRollbackOnParticipationFailure(boolean globalRollbackOnParticipationFailure) {
		this.globalRollbackOnParticipationFailure = globalRollbackOnParticipationFailure;
	}

	/**
	 * 返回参与事务失败后是否将现有事务全局标记为仅回滚。
	 * Return whether to globally mark an existing transaction as rollback-only
	 * after a participating transaction failed.
	 */
	public final boolean isGlobalRollbackOnParticipationFailure() {
		return this.globalRollbackOnParticipationFailure;
	}

	/**
	 * 设置在事务被全局标记为仅回滚时是否提前失败。
	 * Set whether to fail early in case of the transaction being globally marked
	 * as rollback-only.
	 * 默认值为“false”，仅在最外层事务边界处导致意外的回滚异常。
	 * 打开此标志可在首次检测到全局仅回滚标记时（即使是在内部事务边界内）导致UnexpectedRollbackException。
	 * <p>Default is "false", only causing an UnexpectedRollbackException at the
	 * outermost transaction boundary. Switch this flag on to cause an
	 * UnexpectedRollbackException as early as the global rollback-only marker
	 * has been first detected, even from within an inner transaction boundary.
	 * 请注意，在Spring 2.0中，全局仅回滚标记的早期故障行为已经统一起来:
	 * 所有事务管理器在默认情况下只会在最外层事务边界处导致意想不到的drollbackexception。
	 * 例如，这允许在操作失败并且事务永远不会完成之后继续单元测试。
	 * 所有事务管理器只有在此标志被明确设置为“true”时才会更早失败。
	 * <p>Note that, as of Spring 2.0, the fail-early behavior for global
	 * rollback-only markers has been unified: All transaction managers will by
	 * default only cause UnexpectedRollbackException at the outermost transaction
	 * boundary. This allows, for example, to continue unit tests even after an
	 * operation failed and the transaction will never be completed. All transaction
	 * managers will only fail earlier if this flag has explicitly been set to "true".
	 * @since 2.0
	 * @see org.springframework.transaction.UnexpectedRollbackException
	 */
	public final void setFailEarlyOnGlobalRollbackOnly(boolean failEarlyOnGlobalRollbackOnly) {
		this.failEarlyOnGlobalRollbackOnly = failEarlyOnGlobalRollbackOnly;
	}

	/**
	 * 如果事务被全局标记为仅回滚，则返回是否提前失败。
	 * Return whether to fail early in case of the transaction being globally marked
	 * as rollback-only.
	 * @since 2.0
	 */
	public final boolean isFailEarlyOnGlobalRollbackOnly() {
		return this.failEarlyOnGlobalRollbackOnly;
	}

	/**
	 * 设置在doCommit调用失败时是否应执行doRollback。通常不需要，因此要避免，
	 * 因为它可能会用后续的回滚异常重写提交异常。
	 * Set whether {@code doRollback} should be performed on failure of the
	 * {@code doCommit} call. Typically not necessary and thus to be avoided,
	 * as it can potentially override the commit exception with a subsequent
	 * rollback exception.
	 * <p>Default is "false".
	 * @see #doCommit
	 * @see #doRollback
	 */
	public final void setRollbackOnCommitFailure(boolean rollbackOnCommitFailure) {
		this.rollbackOnCommitFailure = rollbackOnCommitFailure;
	}

	/**
	 * 返回在doCommit调用失败时是否应执行doRollback。
	 * Return whether {@code doRollback} should be performed on failure of the
	 * {@code doCommit} call.
	 */
	public final boolean isRollbackOnCommitFailure() {
		return this.rollbackOnCommitFailure;
	}


	//---------------------------------------------------------------------
	// Implementation of PlatformTransactionManager
	//---------------------------------------------------------------------

	/**
	 * 获取当前事务状态
	 * 实现事务传播行为，委托给doGetTransaction、isExistingTransaction和doBegin
	 * This implementation handles propagation behavior. Delegates to
	 * {@code doGetTransaction}, {@code isExistingTransaction}
	 * and {@code doBegin}.
	 * @see #doGetTransaction
	 * @see #isExistingTransaction
	 * @see #doBegin
	 */
	@Override
	public final TransactionStatus getTransaction(@Nullable TransactionDefinition definition)
			throws TransactionException {

		//如果没有给出事务定义，则使用默认值。
		// Use defaults if no transaction definition given.
		TransactionDefinition def = (definition != null ? definition : TransactionDefinition.withDefaults());

		//获取当前事务状态的事务对象
		Object transaction = doGetTransaction();
		boolean debugEnabled = logger.isDebugEnabled();

		//判断这个事务是不是一个已经开启的事务
		if (isExistingTransaction(transaction)) {
			// 找到现有事务->检查传播行为以了解如何操作。
			// Existing transaction found -> check propagation behavior to find out how to behave.
			// 如果已经有开启的事务，则进行spring事务的传播行为处理
			return handleExistingTransaction(def, transaction, debugEnabled);
		}

		// 检查新事务的超时时间是否超过spring事务管理设置的超时时间。
		// Check definition settings for new transaction.
		if (def.getTimeout() < TransactionDefinition.TIMEOUT_DEFAULT) {
			throw new InvalidTimeoutException("Invalid transaction timeout", def.getTimeout());
		}

		// 事务传播行为是MANDATORY支持当前事务；如果当前事务不存在，则引发异常
		// No existing transaction found -> check propagation behavior to find out how to proceed.
		if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_MANDATORY) {
			throw new IllegalTransactionStateException(
					"No existing transaction found for transaction marked with propagation 'mandatory'");
		}
		//如果事务定义传播行为是：
		// PROPAGATION_REQUIRED支持当前事务；如果不存在，则创建新事务
		// PROPAGATION_REQUIRES_NEW创建新事务，如果当前事务存在，则暂停当前事务
		// PROPAGATION_NESTED如果当前事务存在，则在嵌套事务中执行

		else if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED ||
				def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW ||
				def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {
			//触发最后一个逻辑，事务活动状态和事务通过都是null，直接空过返回null，这里接收的挂起资源也是null
			SuspendedResourcesHolder suspendedResources = suspend(null);
			if (debugEnabled) {
				logger.debug("Creating new transaction with name [" + def.getName() + "]: " + def);
			}
			try {
				//创建一个新的事务状态并返回
				return startTransaction(def, transaction, debugEnabled, suspendedResources);
			}
			catch (RuntimeException | Error ex) {
				//恢复空的挂起资源
				resume(null, suspendedResources);
				throw ex;
			}
		}
		else {
			// 创建“空”事务：没有实际事务，但可能是同步。
			// Create "empty" transaction: no actual transaction, but potentially synchronization.
			if (def.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT && logger.isWarnEnabled()) {
				logger.warn("Custom isolation level specified but no actual transaction initiated; " +
						"isolation level will effectively be ignored: " + def);
			}
			//是否激活事务同步管理
			boolean newSynchronization = (getTransactionSynchronization() == SYNCHRONIZATION_ALWAYS);
			//为给定参数创建新的TransactionStatus事务状态
			return prepareTransactionStatus(def, null, true, newSynchronization, debugEnabled, null);
		}
	}

	/**
	 * 创建一个新的事务
	 * Start a new transaction.
	 */
	private TransactionStatus startTransaction(TransactionDefinition definition, Object transaction,
			boolean debugEnabled, @Nullable SuspendedResourcesHolder suspendedResources) {

		//判断是否需要进行事务同步管理，true是需要进行，false是不需要进行同步管理
		boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);
		//根据指定参数创建一个新的事务状态
		DefaultTransactionStatus status = newTransactionStatus(
				definition, transaction, true, newSynchronization, debugEnabled, suspendedResources);
		//交给模板方法创建新的事务
		doBegin(transaction, definition);
		//根据需要初始化事务同步管理器
		prepareSynchronization(status, definition);
		return status;
	}

	/**
	 * TODO 【alex】 spring事务的传播行为处理
	 * 为现有事务创建TransactionStatus。
	 * Create a TransactionStatus for an existing transaction.
	 */
	private TransactionStatus handleExistingTransaction(
			TransactionDefinition definition, Object transaction, boolean debugEnabled)
			throws TransactionException {

		//如果是NEVER传播行为，则引发异常
		if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NEVER) {
			throw new IllegalTransactionStateException(
					"Existing transaction found for transaction marked with propagation 'never'");
		}

		//如果是NOT_SUPPORTED传播行为，则不支持当前事务
		if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NOT_SUPPORTED) {
			if (debugEnabled) {
				logger.debug("Suspending current transaction");
			}
			//挂起当前事务
			Object suspendedResources = suspend(transaction);
			//是否激活事务同步管理
			boolean newSynchronization = (getTransactionSynchronization() == SYNCHRONIZATION_ALWAYS);
			//为给定参数创建新的TransactionStatus事务状态，同时根据需要初始化事务同步管理器
			return prepareTransactionStatus(
					definition, null, false, newSynchronization, debugEnabled, suspendedResources);
		}

		//如果是REQUIRES_NEW传播行为，则创建新的事务并挂起当前事务
		if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
			if (debugEnabled) {
				logger.debug("Suspending current transaction, creating new transaction with name [" +
						definition.getName() + "]");
			}
			//挂起当前事务
			SuspendedResourcesHolder suspendedResources = suspend(transaction);
			try {
				//开启新的事务
				return startTransaction(definition, transaction, debugEnabled, suspendedResources);
			}
			catch (RuntimeException | Error beginEx) {
				//内部事务开启失败后恢复外部事务
				resumeAfterBeginException(transaction, suspendedResources, beginEx);
				throw beginEx;
			}
		}

		//如果是NESTED传播行为，则嵌套事务运行
		if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {
			//如果不支持嵌套事务则抛出异常
			if (!isNestedTransactionAllowed()) {
				throw new NestedTransactionNotSupportedException(
						"Transaction manager does not allow nested transactions by default - " +
						"specify 'nestedTransactionAllowed' property with value 'true'");
			}
			if (debugEnabled) {
				logger.debug("Creating nested transaction with name [" + definition.getName() + "]");
			}
			//如果使用嵌套事务保存点
			if (useSavepointForNestedTransaction()) {
				// 在现有的Spring管理的事务中创建保存点，
				// 通过TransactionStatus实现的SavepointManager API。
				// 通常使用JDBC3.0保存点。从不激活Spring同步。
				// Create savepoint within existing Spring-managed transaction,
				// through the SavepointManager API implemented by TransactionStatus.
				// Usually uses JDBC 3.0 savepoints. Never activates Spring synchronization.
				//指定不是新的事务，也不进入spring同步事务管理
				DefaultTransactionStatus status =
						prepareTransactionStatus(definition, transaction, false, false, debugEnabled, null);
				//创建一个保存点并为事务设置它
				status.createAndHoldSavepoint();
				return status;
			}
			else {
				// 如果不使用嵌套事务保存点
				// 通过显示调用嵌套的begin和commit/rollback调用嵌套事务。
				// 通常只有对于JTA:如果是预先存在的JTA事务，Spring同步可能在这里被激活。
				// Nested transaction through nested begin and commit/rollback calls.
				// Usually only for JTA: Spring synchronization might get activated here
				// in case of a pre-existing JTA transaction.
				return startTransaction(definition, transaction, debugEnabled, null);
			}
		}

		//SUPPORTS传播级别，支持当前事务，如果没有事务则以非事务提交。
		//REQUIRED传播级别.支持当前事务；如果不存在，则创建新事务
		// Assumably PROPAGATION_SUPPORTS or PROPAGATION_REQUIRED.
		if (debugEnabled) {
			logger.debug("Participating in existing transaction");
		}
		//如果在参与事务之前应验证现有事务
		if (isValidateExistingTransaction()) {
			//如果不是数据库默认级别
			if (definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT) {
				//返回当前事务的隔离级别
				Integer currentIsolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
				//如果事务管理器没有设置当前事务的隔离级别，或者事务管理器的隔离级别和当前事务的隔离级别不同，抛出异常
				if (currentIsolationLevel == null || currentIsolationLevel != definition.getIsolationLevel()) {
					Constants isoConstants = DefaultTransactionDefinition.constants;
					throw new IllegalTransactionStateException("Participating transaction with definition [" +
							definition + "] specifies isolation level which is incompatible with existing transaction: " +
							(currentIsolationLevel != null ?
									isoConstants.toCode(currentIsolationLevel, DefaultTransactionDefinition.PREFIX_ISOLATION) :
									"(unknown)"));
				}
			}
			//如果当前事务不是只读事务
			if (!definition.isReadOnly()) {
				//但是当前事务管理器里描述的是只读事务，抛出异常
				if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
					throw new IllegalTransactionStateException("Participating transaction with definition [" +
							definition + "] is not marked as read-only but existing transaction is");
				}
			}
		}
		//是否需要事务同步管理
		boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);
		//为给定参数创建新的TransactionStatus事务状态
		return prepareTransactionStatus(definition, transaction, false, newSynchronization, debugEnabled, null);
	}

	/**
	 * 为给定参数创建新的TransactionStatus事务状态
	 * Create a new TransactionStatus for the given arguments,
	 * 同时根据需要初始化事务同步
	 * also initializing transaction synchronization as appropriate.
	 * @see #newTransactionStatus
	 * @see #prepareTransactionStatus
	 */
	protected final DefaultTransactionStatus prepareTransactionStatus(
			TransactionDefinition definition, @Nullable Object transaction, boolean newTransaction,
			boolean newSynchronization, boolean debug, @Nullable Object suspendedResources) {
		//包装新的事务状态
		DefaultTransactionStatus status = newTransactionStatus(
				definition, transaction, newTransaction, newSynchronization, debug, suspendedResources);
		//如果是新事务，创建激活事务同步管理器
		prepareSynchronization(status, definition);
		return status;
	}

	/**
	 * 根据指定参数创建一个新的事务状态
	 * Create a TransactionStatus instance for the given arguments.
	 */
	protected DefaultTransactionStatus newTransactionStatus(
			TransactionDefinition definition, @Nullable Object transaction, boolean newTransaction,
			boolean newSynchronization, boolean debug, @Nullable Object suspendedResources) {

		//新同步事务 == 需要进行事务同步管理 并且 事务管理器中没有活动的事务
		boolean actualNewSynchronization = newSynchronization &&
				!TransactionSynchronizationManager.isSynchronizationActive();
		//创建一个新的事务状态
		return new DefaultTransactionStatus(
				transaction, newTransaction, actualNewSynchronization,
				definition.isReadOnly(), debug, suspendedResources);
	}

	/**
	 * 根据需要初始化事务同步管理器。
	 * Initialize transaction synchronization as appropriate.
	 */
	protected void prepareSynchronization(DefaultTransactionStatus status, TransactionDefinition definition) {
		//如果是一个新的事务同步管理，则初始化事务同步管理器
		if (status.isNewSynchronization()) {
			//根据当前是否有活动事务设置是否有活动的事务
			TransactionSynchronizationManager.setActualTransactionActive(status.hasTransaction());
			//根据用户配置的隔离级别或者默认隔离级别来设置
			TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(
					definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT ?
							definition.getIsolationLevel() : null);
			//根据当前事务是否只读来设置
			TransactionSynchronizationManager.setCurrentTransactionReadOnly(definition.isReadOnly());
			//根据当前事务名称来设置
			TransactionSynchronizationManager.setCurrentTransactionName(definition.getName());
			//激活事务同步管理器
			TransactionSynchronizationManager.initSynchronization();
		}
	}

	/**
	 * 确定用于给定事务定义的实际超时时间。如果事务没有定义超时时间，则返回到此管理器的默认超时。
	 * Determine the actual timeout to use for the given definition.
	 * Will fall back to this manager's default timeout if the
	 * transaction definition doesn't specify a non-default value.
	 * @param definition the transaction definition
	 * @return the actual timeout to use
	 * @see org.springframework.transaction.TransactionDefinition#getTimeout()
	 * @see #setDefaultTimeout
	 */
	protected int determineTimeout(TransactionDefinition definition) {
		//如果事务设置了超时时间
		if (definition.getTimeout() != TransactionDefinition.TIMEOUT_DEFAULT) {
			//返回事务的超时时间
			return definition.getTimeout();
		}
		//返回默认的超时时间
		return getDefaultTimeout();
	}


	/**
	 * 挂起给定的事务。先挂起多线程事务同步管理，然后委托给{@code doSuspend}模板方法。
	 * Suspend the given transaction. Suspends transaction synchronization first,
	 * then delegates to the {@code doSuspend} template method.
	 * @param transaction the current transaction object
	 * (or {@code null} to just suspend active synchronizations, if any)
	 * @return an object that holds suspended resources
	 * (or {@code null} if neither transaction nor synchronization active)
	 * @see #doSuspend
	 * @see #resume
	 */
	@Nullable
	protected final SuspendedResourcesHolder suspend(@Nullable Object transaction) throws TransactionException {
		//从线程变量里查看多线程事务同步管理对象里的事务是否开启
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			//如果开启了多线程事务同步管理，挂起所有当前事务同步管理对象里的事务，返回挂起的事务对象
			List<TransactionSynchronization> suspendedSynchronizations = doSuspendSynchronization();
			try {
				Object suspendedResources = null;
				if (transaction != null) {
					//交给模板方法，挂起当前事务
					suspendedResources = doSuspend(transaction);
				}
				//获取当前事务管理的名称
				String name = TransactionSynchronizationManager.getCurrentTransactionName();
				//清空当前事务管理的名称
				TransactionSynchronizationManager.setCurrentTransactionName(null);
				//获取当前事务管理的事务是否是只读
				boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
				//设置当前事务管理的事务不是只读
				TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
				//获取当前事务管理的事务等级
				Integer isolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
				//清空当前事务管理的事务等级
				TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
				//获取当前线程是否有活动事务
				boolean wasActive = TransactionSynchronizationManager.isActualTransactionActive();
				//设置当前线程没有活动事务
				TransactionSynchronizationManager.setActualTransactionActive(false);
				//将挂起前的事务和同步事务状态包装并返回
				return new SuspendedResourcesHolder(
						suspendedResources, suspendedSynchronizations, name, readOnly, isolationLevel, wasActive);
			}
			catch (RuntimeException | Error ex) {
				// 挂起事务-原始事务仍处于活动状态。。。
				// 重新激活当前线程的事务同步管理器，并恢复所有给定的同步事务。
				// doSuspend failed - original transaction is still active...
				doResumeSynchronization(suspendedSynchronizations);
				throw ex;
			}
		}
		else if (transaction != null) {
			// 事务处于活动状态，但没有同步处于活动状态。
			// Transaction active but no synchronization active.
			// 挂起当前事务
			Object suspendedResources = doSuspend(transaction);
			//将挂起前的事务包装并返回
			return new SuspendedResourcesHolder(suspendedResources);
		}
		else {
			// 既没有事务和也没有同步事务。
			// Neither transaction nor synchronization active.
			return null;
		}
	}

	/**
	 * 恢复给定的事务。doResume模板方法，然后开启事务同步管理。
	 * Resume the given transaction. Delegates to the {@code doResume}
	 * template method first, then resuming transaction synchronization.
	 * @param transaction the current transaction object
	 * @param resourcesHolder the object that holds suspended resources,
	 * as returned by {@code suspend} (or {@code null} to just
	 * resume synchronizations, if any)
	 * @see #doResume
	 * @see #suspend
	 */
	protected final void resume(@Nullable Object transaction, @Nullable SuspendedResourcesHolder resourcesHolder)
			throws TransactionException {

		if (resourcesHolder != null) {
			//获取挂起的资源
			Object suspendedResources = resourcesHolder.suspendedResources;
			if (suspendedResources != null) {
				//恢复当前事务的资源
				doResume(transaction, suspendedResources);
			}
			//获取事务同步管理器
			List<TransactionSynchronization> suspendedSynchronizations = resourcesHolder.suspendedSynchronizations;
			if (suspendedSynchronizations != null) {
				//恢复挂起事务的活动状态
				TransactionSynchronizationManager.setActualTransactionActive(resourcesHolder.wasActive);
				//恢复挂起事务的隔离级别
				TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(resourcesHolder.isolationLevel);
				//恢复挂起事务的只读标识
				TransactionSynchronizationManager.setCurrentTransactionReadOnly(resourcesHolder.readOnly);
				//恢复挂起事务的名称
				TransactionSynchronizationManager.setCurrentTransactionName(resourcesHolder.name);
				//重新激活当前线程的事务同步管理器，并恢复所有给定的同步事务。
				doResumeSynchronization(suspendedSynchronizations);
			}
		}
	}

	/**
	 * 内部事务开启失败后恢复外部事务。
	 * Resume outer transaction after inner transaction begin failed.
	 */
	private void resumeAfterBeginException(
			Object transaction, @Nullable SuspendedResourcesHolder suspendedResources, Throwable beginEx) {

		try {
			resume(transaction, suspendedResources);
		}
		catch (RuntimeException | Error resumeEx) {
			String exMessage = "Inner transaction begin exception overridden by outer transaction resume exception";
			logger.error(exMessage, beginEx);
			throw resumeEx;
		}
	}

	/**
	 * 挂起所有当前多线程事务同步管理对象里的事务，返回挂起的事务对象
	 * Suspend all current synchronizations and deactivate transaction
	 * synchronization for the current thread.
	 * @return the List of suspended TransactionSynchronization objects
	 */
	private List<TransactionSynchronization> doSuspendSynchronization() {
		List<TransactionSynchronization> suspendedSynchronizations =
				//获取当前线程的所有已注册同步的不可修改快照列表
				TransactionSynchronizationManager.getSynchronizations();
		for (TransactionSynchronization synchronization : suspendedSynchronizations) {
			//将所有的事务同步一个个挂起
			synchronization.suspend();
		}
		//清除线程变量中的事务同步管理对象
		TransactionSynchronizationManager.clearSynchronization();
		return suspendedSynchronizations;
	}

	/**
	 * 重新激活当前线程的事务同步管理器，并恢复所有给定的同步事务。
	 * Reactivate transaction synchronization for the current thread
	 * and resume all given synchronizations.
	 * @param suspendedSynchronizations a List of TransactionSynchronization objects
	 */
	private void doResumeSynchronization(List<TransactionSynchronization> suspendedSynchronizations) {
		//为当前线程激活事务同步管理器
		TransactionSynchronizationManager.initSynchronization();
		for (TransactionSynchronization synchronization : suspendedSynchronizations) {
			//恢复事务
			synchronization.resume();
			//将事务注册到事务同步管理器
			TransactionSynchronizationManager.registerSynchronization(synchronization);
		}
	}


	/**
	 * commit的这个实现处理参与现有事务和编程回滚请求。
	 * 委托给{@code isRollbackOnly}，{@code doCommit}
	 * This implementation of commit handles participating in existing
	 * transactions and programmatic rollback requests.
	 * Delegates to {@code isRollbackOnly}, {@code doCommit}
	 * and {@code rollback}.
	 * @see org.springframework.transaction.TransactionStatus#isRollbackOnly()
	 * @see #doCommit
	 * @see #rollback
	 */
	@Override
	public final void commit(TransactionStatus status) throws TransactionException {

		//如果此事务是否已完成，抛出异常
		if (status.isCompleted()) {
			throw new IllegalTransactionStateException(
					"Transaction is already completed - do not call commit or rollback more than once per transaction");
		}

		DefaultTransactionStatus defStatus = (DefaultTransactionStatus) status;
		//检查当前事务的回滚标识，如果需要回滚
		if (defStatus.isLocalRollbackOnly()) {
			if (defStatus.isDebug()) {
				logger.debug("Transactional code has requested rollback");
			}
			//进行事务回滚
			processRollback(defStatus, false);
			return;
		}

		//如果 全局事务回滚标记时不需要提交  并且  当前全局事务是需要回滚
		if (!shouldCommitOnGlobalRollbackOnly() && defStatus.isGlobalRollbackOnly()) {
			if (defStatus.isDebug()) {
				logger.debug("Global transaction is marked as rollback-only but transactional code requested commit");
			}
			//进行事务回滚，并试图引发异常
			processRollback(defStatus, true);
			return;
		}
		//处理提交
		processCommit(defStatus);
	}

	/**
	 * 处理提交
	 * Process an actual commit.
	 * 已检查并应用仅回滚标志。
	 * Rollback-only flags have already been checked and applied.
	 * @param status object representing the transaction
	 * @throws TransactionException in case of commit failure
	 */
	private void processCommit(DefaultTransactionStatus status) throws TransactionException {
		try {
			//是否调用了提交前的回调函数
			boolean beforeCompletionInvoked = false;

			try {
				//事务回滚标识
				boolean unexpectedRollback = false;
				//准备提交，在commit同步回调发生之前执行。
				prepareForCommit(status);
				//触发事务提交之前回调函数
				triggerBeforeCommit(status);
				//触发执行事务提交/回滚之前的回调函数
				triggerBeforeCompletion(status);
				//调用了提交前的回调函数
				beforeCompletionInvoked = true;

				//如果当前事务有保存点
				if (status.hasSavepoint()) {
					if (status.isDebug()) {
						logger.debug("Releasing transaction savepoint");
					}
					//使用全局事务回滚标识
					unexpectedRollback = status.isGlobalRollbackOnly();
					//释放持有的保存点
					status.releaseHeldSavepoint();
				}
				//如果是新事务
				else if (status.isNewTransaction()) {
					if (status.isDebug()) {
						logger.debug("Initiating transaction commit");
					}
					//使用全局事务回滚标识
					unexpectedRollback = status.isGlobalRollbackOnly();
					//进行提交，这里即使标识为回滚也要提交，由catch或者下方的抛出处理
					doCommit(status);
				}
				//如果事务被全局标记为仅回滚需要提前失败
				else if (isFailEarlyOnGlobalRollbackOnly()) {
					//使用全局事务回滚标识
					unexpectedRollback = status.isGlobalRollbackOnly();
				}

				// 如果全局标记为仅回滚，但仍然没有从commit中获得相应的异常，则抛出异常
				// Throw UnexpectedRollbackException if we have a global rollback-only
				// marker but still didn't get a corresponding exception from commit.
				if (unexpectedRollback) {
					throw new UnexpectedRollbackException(
							"Transaction silently rolled back because it has been marked as rollback-only");
				}
			}
			catch (UnexpectedRollbackException ex) {
				// 只能由doCommit方法引起
				// can only be caused by doCommit
				//触发事务提交/回滚后调用回调函数。
				triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);
				throw ex;
			}
			catch (TransactionException ex) {
				// 只能由doCommit方法引起
				// can only be caused by doCommit
				//如果在提交调用失败时是否应执行回滚
				if (isRollbackOnCommitFailure()) {
					//在提交异常时进行回滚
					doRollbackOnCommitException(status, ex);
				}
				else {
					//触发事务提交/回滚后调用回调函数。
					triggerAfterCompletion(status, TransactionSynchronization.STATUS_UNKNOWN);
				}
				throw ex;
			}
			catch (RuntimeException | Error ex) {
				//如果没有调用提交前的回调函数
				if (!beforeCompletionInvoked) {
					//触发执行事务提交/回滚之前的回调函数
					triggerBeforeCompletion(status);
				}
				//在提交异常时进行回滚
				doRollbackOnCommitException(status, ex);
				throw ex;
			}

			// 触发afterCommit回调，在那里抛出一个异常
			// 传播到调用方，但事务仍被视为已提交。
			// Trigger afterCommit callbacks, with an exception thrown there
			// propagated to callers but the transaction still considered as committed.
			try {
				//触发事务提交后调用回调函数
				triggerAfterCommit(status);
			}
			finally {
				//触发事务提交/回滚后调用回调函数。
				triggerAfterCompletion(status, TransactionSynchronization.STATUS_COMMITTED);
			}

		}
		finally {
			//事务完成后清理
			cleanupAfterCompletion(status);
		}
	}

	/**
	 * 此回滚实现处理参与现有事务的情况。委托给{@code doRollback}和{@code doSetRollbackOnly}。
	 * This implementation of rollback handles participating in existing
	 * transactions. Delegates to {@code doRollback} and
	 * {@code doSetRollbackOnly}.
	 * @see #doRollback
	 * @see #doSetRollbackOnly
	 */
	@Override
	public final void rollback(TransactionStatus status) throws TransactionException {
		//如果事务已经完成，抛出异常
		if (status.isCompleted()) {
			throw new IllegalTransactionStateException(
					"Transaction is already completed - do not call commit or rollback more than once per transaction");
		}

		DefaultTransactionStatus defStatus = (DefaultTransactionStatus) status;
		//处理实际回滚
		processRollback(defStatus, false);
	}

	/**
	 * 处理实际回滚。
	 * Process an actual rollback.
	 * 已检查完成标志。
	 * The completed flag has already been checked.
	 * @param status object representing the transaction
	 * @throws TransactionException in case of rollback failure
	 */
	private void processRollback(DefaultTransactionStatus status, boolean unexpected) {
		try {
			//回滚异常标识，由外部设置的回滚标识带来
			boolean unexpectedRollback = unexpected;

			try {
				//执行提交此次事务前的回调函数
				triggerBeforeCompletion(status);

				//如果事务有保存点
				if (status.hasSavepoint()) {
					if (status.isDebug()) {
						logger.debug("Rolling back transaction to savepoint");
					}
					//回滚到为事务保留的保存点，然后立即释放该保存点
					status.rollbackToHeldSavepoint();
				}
				//如果是一个新事务
				else if (status.isNewTransaction()) {
					if (status.isDebug()) {
						logger.debug("Initiating transaction rollback");
					}
					//直接进行回滚
					doRollback(status);
				}
				//如果参与了其他事务
				else {
					// Participating in larger transaction
					//如果存在活动的实际事务
					if (status.hasTransaction()) {
						//如果当前事务标记为需要回滚  或者  全局事务标记为回滚
						if (status.isLocalRollbackOnly() || isGlobalRollbackOnParticipationFailure()) {
							if (status.isDebug()) {
								logger.debug("Participating transaction failed - marking existing transaction as rollback-only");
							}
							//仅设置给定的事务回滚
							doSetRollbackOnly(status);
						}
						else {
							if (status.isDebug()) {
								logger.debug("Participating transaction failed - letting transaction originator decide on rollback");
							}
						}
					}
					else {
						logger.debug("Should roll back transaction but cannot - no transaction available");
					}
					// 意外的回退只在我们被要求提前失败时才重要
					// Unexpected rollback only matters here if we're asked to fail early
					// 如果没有设置异常时提前失败，则直接将异常标识修改为false
					if (!isFailEarlyOnGlobalRollbackOnly()) {
						unexpectedRollback = false;
					}
				}
			}
			catch (RuntimeException | Error ex) {
				//回滚时发生异常
				//触发事务提交/回滚后调用回调函数
				triggerAfterCompletion(status, TransactionSynchronization.STATUS_UNKNOWN);
				throw ex;
			}
			//触发事务提交/回滚后调用回调函数
			triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);

			// 如果我们有全局仅回滚标记，则引发意外的回滚异常
			// Raise UnexpectedRollbackException if we had a global rollback-only marker
			if (unexpectedRollback) {
				throw new UnexpectedRollbackException(
						"Transaction rolled back because it has been marked as rollback-only");
			}
		}
		finally {
			//事务完成后清理
			cleanupAfterCompletion(status);
		}
	}

	/**
	 * 在提交异常时进行回滚
	 * Invoke {@code doRollback}, handling rollback exceptions properly.
	 * @param status object representing the transaction
	 * @param ex the thrown application exception or error
	 * @throws TransactionException in case of rollback failure
	 * @see #doRollback
	 */
	private void doRollbackOnCommitException(DefaultTransactionStatus status, Throwable ex) throws TransactionException {
		try {
			//如果是一个新事务
			if (status.isNewTransaction()) {
				if (status.isDebug()) {
					logger.debug("Initiating transaction rollback after commit exception", ex);
				}
				//回滚事务
				doRollback(status);
			}
			//如果存在活动的事务 并且 需要在参数事务失败时记录全局回滚标记
			else if (status.hasTransaction() && isGlobalRollbackOnParticipationFailure()) {
				if (status.isDebug()) {
					logger.debug("Marking existing transaction as rollback-only after commit exception", ex);
				}
				//设置事务全局回滚标记
				doSetRollbackOnly(status);
			}
		}
		catch (RuntimeException | Error rbex) {
			logger.error("Commit exception overridden by rollback exception", ex);
			triggerAfterCompletion(status, TransactionSynchronization.STATUS_UNKNOWN);
			throw rbex;
		}
		//触发事务提交/回滚后调用回调函数。
		triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);
	}


	/**
	 * 触发事务提交之前回调函数
	 * Trigger {@code beforeCommit} callbacks.
	 * @param status object representing the transaction
	 */
	protected final void triggerBeforeCommit(DefaultTransactionStatus status) {
		//如果是新的事务同步管理器
		if (status.isNewSynchronization()) {
			if (status.isDebug()) {
				logger.trace("Triggering beforeCommit synchronization");
			}
			//在所有的事务同步管理器上触发事务提交之前回调函数
			TransactionSynchronizationUtils.triggerBeforeCommit(status.isReadOnly());
		}
	}

	/**
	 * 触发执行事务提交/回滚之前的回调函数
	 * Trigger {@code beforeCompletion} callbacks.
	 * @param status object representing the transaction
	 */
	protected final void triggerBeforeCompletion(DefaultTransactionStatus status) {
		//如果已为此事务打开新的事务同步管理
		if (status.isNewSynchronization()) {
			if (status.isDebug()) {
				logger.trace("Triggering beforeCompletion synchronization");
			}
			//触发执行事务提交/回滚之前的回调函数
			TransactionSynchronizationUtils.triggerBeforeCompletion();
		}
	}

	/**
	 * 触发事务提交后调用回调函数
	 * Trigger {@code afterCommit} callbacks.
	 * @param status object representing the transaction
	 */
	private void triggerAfterCommit(DefaultTransactionStatus status) {
		//如果开启了新的同步事务管理器
		if (status.isNewSynchronization()) {
			if (status.isDebug()) {
				logger.trace("Triggering afterCommit synchronization");
			}
			//触发所有事务同步管理器上的事务提交后调用回调函数
			TransactionSynchronizationUtils.triggerAfterCommit();
		}
	}

	/**
	 * 触发事务提交/回滚后调用回调函数。
	 * Trigger {@code afterCompletion} callbacks.
	 * @param status object representing the transaction
	 * @param completionStatus completion status according to TransactionSynchronization constants
	 */
	private void triggerAfterCompletion(DefaultTransactionStatus status, int completionStatus) {
		//如果是新的事务同步管理器
		if (status.isNewSynchronization()) {
			//获取事务同步管理器
			List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
			//清除事务同步管理器
			TransactionSynchronizationManager.clearSynchronization();
			//如果没有活动的事务 或者 开启的是事务
			if (!status.hasTransaction() || status.isNewTransaction()) {
				if (status.isDebug()) {
					logger.trace("Triggering afterCompletion synchronization");
				}
				// 当前作用域没有事务或新事务->
				// 立即调用afterCompletion回调
				// No transaction or new transaction for the current scope ->
				// invoke the afterCompletion callbacks immediately
				//立即调用给定Spring 事务管理器对象在事务提交/回滚后调用回调函数。
				invokeAfterCompletion(synchronizations, completionStatus);
			}
			//如果有事务同步管理
			else if (!synchronizations.isEmpty()) {
				// 我们参与的现有事务，控制在这个Spring事务管理器范围之外->
				// 尝试用现有（JTA）事务注册一个afterCompletion回调。
				// Existing transaction that we participate in, controlled outside
				// of the scope of this Spring transaction manager -> try to register
				// an afterCompletion callback with the existing (JTA) transaction.
				//只处理当前事务的事务提交/回滚后调用回调函数
				registerAfterCompletionWithExistingTransaction(status.getTransaction(), synchronizations);
			}
		}
	}

	/**
	 * 实际调用给定Spring 事务管理器对象在事务提交/回滚后调用回调函数。
	 * Actually invoke the {@code afterCompletion} methods of the
	 * given Spring TransactionSynchronization objects.
	 * <p>To be called by this abstract manager itself, or by special implementations
	 * of the {@code registerAfterCompletionWithExistingTransaction} callback.
	 * @param synchronizations a List of TransactionSynchronization objects
	 * @param completionStatus the completion status according to the
	 * constants in the TransactionSynchronization interface
	 * @see #registerAfterCompletionWithExistingTransaction(Object, java.util.List)
	 * @see TransactionSynchronization#STATUS_COMMITTED
	 * @see TransactionSynchronization#STATUS_ROLLED_BACK
	 * @see TransactionSynchronization#STATUS_UNKNOWN
	 */
	protected final void invokeAfterCompletion(List<TransactionSynchronization> synchronizations, int completionStatus) {
		//立即调用给定Spring 事务管理器对象在事务提交/回滚后调用回调函数。
		TransactionSynchronizationUtils.invokeAfterCompletion(synchronizations, completionStatus);
	}

	/**
	 * 事务完成后清理，如有必要清除同步，并在完成后调用docleanup。
	 * Clean up after completion, clearing synchronization if necessary,
	 * and invoking doCleanupAfterCompletion.
	 * @param status object representing the transaction
	 * @see #doCleanupAfterCompletion
	 */
	private void cleanupAfterCompletion(DefaultTransactionStatus status) {
		//将此事务标记为已完成，即已提交或已回滚。
		status.setCompleted();
		//如果打开了新的事务同步管理器
		if (status.isNewSynchronization()) {
			//清理事务同步管理器
			TransactionSynchronizationManager.clear();
		}
		//如果是个新事务
		if (status.isNewTransaction()) {
			//清理当前事务的资源
			doCleanupAfterCompletion(status.getTransaction());
		}
		//如果有挂起的资源
		if (status.getSuspendedResources() != null) {
			if (status.isDebug()) {
				logger.debug("Resuming suspended transaction after completion of inner transaction");
			}
			//获取事务
			Object transaction = (status.hasTransaction() ? status.getTransaction() : null);
			//恢复当前事务挂起的资源
			resume(transaction, (SuspendedResourcesHolder) status.getSuspendedResources());
		}
	}


	//---------------------------------------------------------------------
	// Template methods to be implemented in subclasses
	//---------------------------------------------------------------------

	/**
	 * 返回当前事务状态的事务对象。
	 * Return a transaction object for the current transaction state.
	 * 返回的对象通常特定于具体的transaction*manager实现，以可修改的方式携带相应的事务状态。
	 * 此对象将直接或作为DefaultTransactionStatus实例的一部分传递到其他模板方法上（如doBegin和doCommit）。
	 * <p>The returned object will usually be specific to the concrete transaction
	 * manager implementation, carrying corresponding transaction state in a
	 * modifiable fashion. This object will be passed into the other template
	 * methods (e.g. doBegin and doCommit), either directly or as part of a
	 * DefaultTransactionStatus instance.
	 * 返回的对象应包含有关任何现有事务的信息，即在事务管理器上的current{@code getTransaction}调用之前已启动的事务。
	 * 因此，{@code-doGetTransaction}实现通常会查找现有事务并在返回的事务对象中存储相应的状态。
	 * <p>The returned object should contain information about any existing
	 * transaction, that is, a transaction that has already started before the
	 * current {@code getTransaction} call on the transaction manager.
	 * Consequently, a {@code doGetTransaction} implementation will usually
	 * look for an existing transaction and store corresponding state in the
	 * returned transaction object.
	 * @return the current transaction object
	 * @throws org.springframework.transaction.CannotCreateTransactionException
	 * if transaction support is not available
	 * @throws TransactionException in case of lookup or system errors
	 * @see #doBegin
	 * @see #doCommit
	 * @see #doRollback
	 * @see DefaultTransactionStatus#getTransaction
	 */
	protected abstract Object doGetTransaction() throws TransactionException;

	/**
	 * 检查给定的事务对象是否是一个现有事务(即已经开始的事务)。
	 * Check if the given transaction object indicates an existing transaction
	 * (that is, a transaction which has already started).
	 * 将根据新事务的指定传播行为对结果进行评估。现有事务可能被挂起(在PROPAGATION_REQUIRES_NEW的情况下)，
	 * 或者新事务可能参与现有事务(在PROPAGATION_REQUIRED的情况下)。
	 * <p>The result will be evaluated according to the specified propagation
	 * behavior for the new transaction. An existing transaction might get
	 * suspended (in case of PROPAGATION_REQUIRES_NEW), or the new transaction
	 * might participate in the existing one (in case of PROPAGATION_REQUIRED).
	 * 默认实现返回{@code false}，假设通常不支持参与现有事务。当然，我们鼓励子类提供这样的支持。
	 * <p>The default implementation returns {@code false}, assuming that
	 * participating in existing transactions is generally not supported.
	 * Subclasses are of course encouraged to provide such support.
	 * @param transaction transaction object returned by doGetTransaction
	 * @return if there is an existing transaction
	 * @throws TransactionException in case of system errors
	 * @see #doGetTransaction
	 */
	protected boolean isExistingTransaction(Object transaction) throws TransactionException {
		return false;
	}

	/**
	 * 返回是否对嵌套事务使用保存点。
	 * Return whether to use a savepoint for a nested transaction.
	 * 默认值为true，这将导致委派到DefaultTransactionStatus以创建和保存保存点。
	 * 如果事务对象未实现SavepointManager接口，则将引发异常。
	 * 否则，将要求SavepointManager创建一个新的savepoint来划分嵌套事务的开始。
	 * <p>Default is {@code true}, which causes delegation to DefaultTransactionStatus
	 * for creating and holding a savepoint. If the transaction object does not implement
	 * the SavepointManager interface, a NestedTransactionNotSupportedException will be
	 * thrown. Else, the SavepointManager will be asked to create a new savepoint to
	 * demarcate the start of the nested transaction.
	 * <p>Subclasses can override this to return {@code false}, causing a further
	 * call to {@code doBegin} - within the context of an already existing transaction.
	 * The {@code doBegin} implementation needs to handle this accordingly in such
	 * a scenario. This is appropriate for JTA, for example.
	 * @see DefaultTransactionStatus#createAndHoldSavepoint
	 * @see DefaultTransactionStatus#rollbackToHeldSavepoint
	 * @see DefaultTransactionStatus#releaseHeldSavepoint
	 * @see #doBegin
	 */
	protected boolean useSavepointForNestedTransaction() {
		return true;
	}

	/**
	 * 根据给定的transaction事务定义以开始新事务。不必关心应用传播行为，
	 * 因为这个抽象管理器实现类已经处理过了。
	 * Begin a new transaction with semantics according to the given transaction
	 * definition. Does not have to care about applying the propagation behavior,
	 * as this has already been handled by this abstract manager.
	 * 当事务管理器决定实际启动新事务时，将调用此方法。以前没有任何事务，或者以前的事务已被挂起。
	 * <p>This method gets called when the transaction manager has decided to actually
	 * start a new transaction. Either there wasn't any transaction before, or the
	 * previous transaction has been suspended.
	 * 一个特殊的场景是没有保存点的嵌套事务：如果{@code useSavepointForNestedTransaction（）}返回“false”，
	 * 则在必要时将调用此方法来启动嵌套事务。在这样的上下文中，将有一个活动事务：
	 * 此方法的实现必须检测到此情况并启动适当的嵌套事务。
	 * <p>A special scenario is a nested transaction without savepoint: If
	 * {@code useSavepointForNestedTransaction()} returns "false", this method
	 * will be called to start a nested transaction when necessary. In such a context,
	 * there will be an active transaction: The implementation of this method has
	 * to detect this and start an appropriate nested transaction.
	 * @param transaction transaction object returned by {@code doGetTransaction}
	 * @param definition a TransactionDefinition instance, describing propagation
	 * behavior, isolation level, read-only flag, timeout, and transaction name
	 * @throws TransactionException in case of creation or system errors
	 * @throws org.springframework.transaction.NestedTransactionNotSupportedException
	 * if the underlying transaction does not support nesting
	 */
	protected abstract void doBegin(Object transaction, TransactionDefinition definition)
			throws TransactionException;

	/**
	 * 挂起当前事务的资源。
	 * Suspend the resources of the current transaction.
	 * 事务同步管理已经挂起。
	 * Transaction synchronization will already have been suspended.
	 * 默认实现抛出异常，假设通常不支持事务挂起。
	 * <p>The default implementation throws a TransactionSuspensionNotSupportedException,
	 * assuming that transaction suspension is generally not supported.
	 * @param transaction transaction object returned by {@code doGetTransaction}
	 * @return an object that holds suspended resources
	 * (will be kept unexamined for passing it into doResume)
	 * @throws org.springframework.transaction.TransactionSuspensionNotSupportedException
	 * if suspending is not supported by the transaction manager implementation
	 * @throws TransactionException in case of system errors
	 * @see #doResume
	 */
	protected Object doSuspend(Object transaction) throws TransactionException {
		throw new TransactionSuspensionNotSupportedException(
				"Transaction manager [" + getClass().getName() + "] does not support transaction suspension");
	}

	/**
	 * 恢复当前事务的资源。随后将恢复事务同步管理。
	 * Resume the resources of the current transaction.
	 * Transaction synchronization will be resumed afterwards.
	 * 默认实现抛出异常，假设通常不支持事务挂起。
	 * <p>The default implementation throws a TransactionSuspensionNotSupportedException,
	 * assuming that transaction suspension is generally not supported.
	 * @param transaction transaction object returned by {@code doGetTransaction}
	 * @param suspendedResources the object that holds suspended resources,
	 * as returned by doSuspend
	 * @throws org.springframework.transaction.TransactionSuspensionNotSupportedException
	 * if resuming is not supported by the transaction manager implementation
	 * @throws TransactionException in case of system errors
	 * @see #doSuspend
	 */
	protected void doResume(@Nullable Object transaction, Object suspendedResources) throws TransactionException {
		throw new TransactionSuspensionNotSupportedException(
				"Transaction manager [" + getClass().getName() + "] does not support transaction suspension");
	}

	/**
	 * 返回是否对已以全局方式标记为回滚的事务调用doCommit。
	 * Return whether to call {@code doCommit} on a transaction that has been
	 * marked as rollback-only in a global fashion.
	 * <p>Does not apply if an application locally sets the transaction to rollback-only
	 * via the TransactionStatus, but only to the transaction itself being marked as
	 * rollback-only by the transaction coordinator.
	 * <p>Default is "false": Local transaction strategies usually don't hold the rollback-only
	 * marker in the transaction itself, therefore they can't handle rollback-only transactions
	 * as part of transaction commit. Hence, AbstractPlatformTransactionManager will trigger
	 * a rollback in that case, throwing an UnexpectedRollbackException afterwards.
	 * <p>Override this to return "true" if the concrete transaction manager expects a
	 * {@code doCommit} call even for a rollback-only transaction, allowing for
	 * special handling there. This will, for example, be the case for JTA, where
	 * {@code UserTransaction.commit} will check the read-only flag itself and
	 * throw a corresponding RollbackException, which might include the specific reason
	 * (such as a transaction timeout).
	 * <p>If this method returns "true" but the {@code doCommit} implementation does not
	 * throw an exception, this transaction manager will throw an UnexpectedRollbackException
	 * itself. This should not be the typical case; it is mainly checked to cover misbehaving
	 * JTA providers that silently roll back even when the rollback has not been requested
	 * by the calling code.
	 * @see #doCommit
	 * @see DefaultTransactionStatus#isGlobalRollbackOnly()
	 * @see DefaultTransactionStatus#isLocalRollbackOnly()
	 * @see org.springframework.transaction.TransactionStatus#setRollbackOnly()
	 * @see org.springframework.transaction.UnexpectedRollbackException
	 * @see javax.transaction.UserTransaction#commit()
	 * @see javax.transaction.RollbackException
	 */
	protected boolean shouldCommitOnGlobalRollbackOnly() {
		return false;
	}

	/**
	 * 准备提交，在commit同步回调发生之前执行。
	 * Make preparations for commit, to be performed before the
	 * {@code beforeCommit} synchronization callbacks occur.
	 * <p>Note that exceptions will get propagated to the commit caller
	 * and cause a rollback of the transaction.
	 * @param status the status representation of the transaction
	 * @throws RuntimeException in case of errors; will be <b>propagated to the caller</b>
	 * (note: do not throw TransactionException subclasses here!)
	 */
	protected void prepareForCommit(DefaultTransactionStatus status) {
	}

	/**
	 * 执行给定事务的实际提交模板方法。
	 * Perform an actual commit of the given transaction.
	 * 实现不需要检查“new transaction”标志或“rollback only”标志；
	 * 这在以前就已经处理过了。通常，将对传入状态中包含的事务对象执行直接提交。
	 * <p>An implementation does not need to check the "new transaction" flag
	 * or the rollback-only flag; this will already have been handled before.
	 * Usually, a straight commit will be performed on the transaction object
	 * contained in the passed-in status.
	 * @param status the status representation of the transaction
	 * @throws TransactionException in case of commit or system errors
	 * @see DefaultTransactionStatus#getTransaction
	 */
	protected abstract void doCommit(DefaultTransactionStatus status) throws TransactionException;

	/**
	 * 执行给定事务的实际回滚方法。
	 * Perform an actual rollback of the given transaction.
	 * 实现不需要检查“new transaction”标志；这在以前就已经处理过了。
	 * 通常，将对传入状态中包含的事务对象执行直接回滚。
	 * <p>An implementation does not need to check the "new transaction" flag;
	 * this will already have been handled before. Usually, a straight rollback
	 * will be performed on the transaction object contained in the passed-in status.
	 * @param status the status representation of the transaction
	 * @throws TransactionException in case of system errors
	 * @see DefaultTransactionStatus#getTransaction
	 */
	protected abstract void doRollback(DefaultTransactionStatus status) throws TransactionException;

	/**
	 * 仅设置给定的事务回滚。仅当当前事务参与现有事务时在回滚时调用。
	 * Set the given transaction rollback-only. Only called on rollback
	 * if the current transaction participates in an existing one.
	 * <p>The default implementation throws an IllegalTransactionStateException,
	 * assuming that participating in existing transactions is generally not
	 * supported. Subclasses are of course encouraged to provide such support.
	 * @param status the status representation of the transaction
	 * @throws TransactionException in case of system errors
	 */
	protected void doSetRollbackOnly(DefaultTransactionStatus status) throws TransactionException {
		throw new IllegalTransactionStateException(
				"Participating in existing transactions is not supported - when 'isExistingTransaction' " +
				"returns true, appropriate 'doSetRollbackOnly' behavior must be provided");
	}

	/**
	 * 将给定的事务同步列表注册到现有事务。
	 * Register the given list of transaction synchronizations with the existing transaction.
	 * 当Spring事务管理器的控制结束，而所有Spring事务同步结束时调用，而事务尚未完成。
	 * <p>Invoked when the control of the Spring transaction manager and thus all Spring
	 * transaction synchronizations end, without the transaction being completed yet. This
	 * is for example the case when participating in an existing JTA or EJB CMT transaction.
	 * <p>The default implementation simply invokes the {@code afterCompletion} methods
	 * immediately, passing in "STATUS_UNKNOWN". This is the best we can do if there's no
	 * chance to determine the actual outcome of the outer transaction.
	 * @param transaction transaction object returned by {@code doGetTransaction}
	 * @param synchronizations a List of TransactionSynchronization objects
	 * @throws TransactionException in case of system errors
	 * @see #invokeAfterCompletion(java.util.List, int)
	 * @see TransactionSynchronization#afterCompletion(int)
	 * @see TransactionSynchronization#STATUS_UNKNOWN
	 */
	protected void registerAfterCompletionWithExistingTransaction(
			Object transaction, List<TransactionSynchronization> synchronizations) throws TransactionException {

		logger.debug("Cannot register Spring after-completion synchronization with existing transaction - " +
				"processing Spring after-completion callbacks immediately, with outcome status 'unknown'");
		//实际调用给定Spring 事务管理器对象在事务提交/回滚后调用回调函数。
		invokeAfterCompletion(synchronizations, TransactionSynchronization.STATUS_UNKNOWN);
	}

	/**
	 * 事务完成后清理资源。
	 * Cleanup resources after transaction completion.
	 * 在执行{@code-doCommit}和{@code-doRollback}后调用。默认实现什么也不做。
	 * <p>Called after {@code doCommit} and {@code doRollback} execution,
	 * on any outcome. The default implementation does nothing.
	 * 不应引发任何异常，而应仅对错误发出警告
	 * <p>Should not throw any exceptions but just issue warnings on errors.
	 * @param transaction transaction object returned by {@code doGetTransaction}
	 */
	protected void doCleanupAfterCompletion(Object transaction) {
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	/**
	 * 序列化支持
	 * @param ois
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		//依赖于默认序列化；反序列化后仅初始化状态。
		// Rely on default serialization; just initialize state after deserialization.
		ois.defaultReadObject();

		//初始化瞬态字段。
		// Initialize transient fields.
		this.logger = LogFactory.getLog(getClass());
	}


	/**
	 * 暂停资源的持有者。
	 * Holder for suspended resources.
	 * 由挂起和恢复内部使用
	 * Used internally by {@code suspend} and {@code resume}.
	 */
	protected static final class SuspendedResourcesHolder {

		/**
		 * 挂起的资源
		 */
		@Nullable
		private final Object suspendedResources;

		@Nullable
		private List<TransactionSynchronization> suspendedSynchronizations;

		/**
		 * 事务名称
		 */
		@Nullable
		private String name;

		/**
		 * 是否只读
		 */
		private boolean readOnly;

		/**
		 * 隔离级别
		 */
		@Nullable
		private Integer isolationLevel;

		/**
		 * 线程中是否有活动事务
		 */
		private boolean wasActive;

		private SuspendedResourcesHolder(Object suspendedResources) {
			this.suspendedResources = suspendedResources;
		}

		private SuspendedResourcesHolder(
				@Nullable Object suspendedResources, List<TransactionSynchronization> suspendedSynchronizations,
				@Nullable String name, boolean readOnly, @Nullable Integer isolationLevel, boolean wasActive) {

			this.suspendedResources = suspendedResources;
			this.suspendedSynchronizations = suspendedSynchronizations;
			this.name = name;
			this.readOnly = readOnly;
			this.isolationLevel = isolationLevel;
			this.wasActive = wasActive;
		}
	}

}
