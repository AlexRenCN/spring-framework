/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.beans.factory.config;

import org.springframework.lang.Nullable;
import org.springframework.util.StringValueResolver;

/**
 * 字符串值解析适配器，用于根据{@link ConfigurableBeanFactory}解析占位符和表达式。
 * {@link StringValueResolver} adapter for resolving placeholders and
 * expressions against a {@link ConfigurableBeanFactory}.
 *
 * 注意，这个适配器也解析表达式，resolvembeddedvalue方法相反。
 * 使用的{@link BeanExpressionContext}用于纯bean工厂，没有为任何要访问的上下文对象指定作用域。
 * <p>Note that this adapter resolves expressions as well, in contrast
 * to the {@link ConfigurableBeanFactory#resolveEmbeddedValue} method.
 * The {@link BeanExpressionContext} used is for the plain bean factory,
 * with no scope specified for any contextual objects to access.
 *
 * @author Juergen Hoeller
 * @since 4.3
 * @see ConfigurableBeanFactory#resolveEmbeddedValue(String)
 * @see ConfigurableBeanFactory#getBeanExpressionResolver()
 * @see BeanExpressionContext
 */
public class EmbeddedValueResolver implements StringValueResolver {

	private final BeanExpressionContext exprContext;

	@Nullable
	private final BeanExpressionResolver exprResolver;


	public EmbeddedValueResolver(ConfigurableBeanFactory beanFactory) {
		this.exprContext = new BeanExpressionContext(beanFactory, null);
		this.exprResolver = beanFactory.getBeanExpressionResolver();
	}


	@Override
	@Nullable
	public String resolveStringValue(String strVal) {
		String value = this.exprContext.getBeanFactory().resolveEmbeddedValue(strVal);
		if (this.exprResolver != null && value != null) {
			Object evaluated = this.exprResolver.evaluate(value, this.exprContext);
			value = (evaluated != null ? evaluated.toString() : null);
		}
		return value;
	}

}
