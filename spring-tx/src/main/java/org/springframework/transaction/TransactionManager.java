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

/**
 * 标记spring的事务管理器实现接口
 * Marker interface for Spring transaction manager implementations,
 * 无论是传统事务管理器还是响应式事务管理器
 * either traditional or reactive.
 *
 * @author Juergen Hoeller
 * @since 5.2
 * @see PlatformTransactionManager
 * @see ReactiveTransactionManager
 */
public interface TransactionManager {

}
