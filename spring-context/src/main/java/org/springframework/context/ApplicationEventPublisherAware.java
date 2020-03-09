/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.context;

import org.springframework.beans.factory.Aware;

/**
 * 如果对象希望实现应用上下文事件发布（通常是ApplicationContext），则实现此接口
 * Interface to be implemented by any object that wishes to be notified
 * of the ApplicationEventPublisher (typically the ApplicationContext)
 * that it runs in.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 1.1.1
 * @see ApplicationContextAware
 */
public interface ApplicationEventPublisherAware extends Aware {

	/**
	 * 设置运行此对象的应用上下文事件发布器。
	 * Set the ApplicationEventPublisher that this object runs in.
	 * 在正常bean属性填充之后但在init回调之前调用，
	 * 如initializengbean的afterPropertiesSet或自定义init方法。
	 * 在ApplicationContextAware的setApplicationContext之前调用。
	 * <p>Invoked after population of normal bean properties but before an init
	 * callback like InitializingBean's afterPropertiesSet or a custom init-method.
	 * Invoked before ApplicationContextAware's setApplicationContext.
	 * @param applicationEventPublisher event publisher to be used by this object
	 */
	void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher);

}
