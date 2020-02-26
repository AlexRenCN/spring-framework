/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.multipart.support;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.apache.commons.logging.LogFactory;

import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;

/**
 * 实现Servlet 3.0标准的表单提交
 * Standard implementation of the {@link MultipartResolver} interface,
 * based on the Servlet 3.0 {@link javax.servlet.http.Part} API.
 * 要作为“ multipartResolver” bean添加到Spring DispatcherServlet上下文中，
 * 无需在bean级别进行任何额外配置（请参见下文）。
 * To be added as "multipartResolver" bean to a Spring DispatcherServlet context,
 * without any extra configuration at the bean level (see below).
 *
 * 为了使用基于Servlet 3.0的多部分解析，
 * 您需要在 {@code web.xml}中用“ multipart-config”部分标记受影响的servlet。
 * 或在程序化Servlet注册中带有{@link javax.servlet.MultipartConfigElement}，
 * 或者（对于自定义servlet类）在Servlet类上带有{@link javax.servlet.annotation.MultipartConfig}注解。
 * <p><b>Note:</b> In order to use Servlet 3.0 based multipart parsing,
 * you need to mark the affected servlet with a "multipart-config" section in
 * {@code web.xml}, or with a {@link javax.servlet.MultipartConfigElement}
 * in programmatic servlet registration, or (in case of a custom servlet class)
 * possibly with a {@link javax.servlet.annotation.MultipartConfig} annotation
 * on your servlet class. Configuration settings such as maximum sizes or
 * storage locations need to be applied at that servlet registration level;
 * Servlet 3.0 does not allow for them to be set at the MultipartResolver level.
 *
 * <pre class="code">
 * public class AppInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {
 *	 // ...
 *	 &#064;Override
 *	 protected void customizeRegistration(ServletRegistration.Dynamic registration) {
 *     // Optionally also set maxFileSize, maxRequestSize, fileSizeThreshold
 *     registration.setMultipartConfig(new MultipartConfigElement("/tmp"));
 *   }
 * }
 * </pre>
 *
 * @author Juergen Hoeller
 * @since 3.1
 * @see #setResolveLazily
 * @see HttpServletRequest#getParts()
 * @see org.springframework.web.multipart.commons.CommonsMultipartResolver
 */
public class StandardServletMultipartResolver implements MultipartResolver {

	/**
	 * 是否延迟解析，默认为false
	 */
	private boolean resolveLazily = false;


	/**
	 * 设置是否在文件或参数访问时延迟解析分段请求。
	 * Set whether to resolve the multipart request lazily at the time of
	 * file or parameter access.
	 * 默认为false，立即解析分段元素，并且在调用resolveMultipart时抛出对应异常
	 * <p>Default is "false", resolving the multipart elements immediately, throwing
	 * corresponding exceptions at the time of the {@link #resolveMultipart} call.
	 * 设置为true，延迟解析分段元素，在获取分段文件或者参数是抛出解析异常
	 * Switch this to "true" for lazy multipart parsing, throwing parse exceptions
	 * once the application attempts to obtain multipart files or parameters.
	 * @since 3.2.9
	 */
	public void setResolveLazily(boolean resolveLazily) {
		this.resolveLazily = resolveLazily;
	}


	@Override
	public boolean isMultipart(HttpServletRequest request) {
		//解析http请求的参数中有没有设置对应的参数
		return StringUtils.startsWithIgnoreCase(request.getContentType(), "multipart/");
	}

	@Override
	public MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) throws MultipartException {
		//包装成为servlet3.0标准的表单提交
		return new StandardMultipartHttpServletRequest(request, this.resolveLazily);
	}

	@Override
	public void cleanupMultipart(MultipartHttpServletRequest request) {
		//如果不是实现自AbstractMultipartHttpServletRequest），
		//或者请求已经解析过
		if (!(request instanceof AbstractMultipartHttpServletRequest) ||
				((AbstractMultipartHttpServletRequest) request).isResolved()) {
			// 为了安全起见：调用所有part文件的删除方法，但是只删除实际存在的文件
			// To be on the safe side: explicitly delete the parts,
			// but only actual file parts (for Resin compatibility)
			try {
				for (Part part : request.getParts()) {
					//判断是否有文件名并获取对应的文件
					if (request.getFile(part.getName()) != null) {
						//如果找得到对应文件则删除
						part.delete();
					}
				}
			}
			catch (Throwable ex) {
				//只为了安全起见，所以这里就算异常也只是打印日志记录
				LogFactory.getLog(getClass()).warn("Failed to perform cleanup of multipart items", ex);
			}
		}
	}

}
