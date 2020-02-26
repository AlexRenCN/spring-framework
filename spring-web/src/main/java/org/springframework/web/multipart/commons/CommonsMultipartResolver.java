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

package org.springframework.web.multipart.commons;

import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import com.sun.xml.internal.bind.v2.TODO;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import org.springframework.util.Assert;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.AbstractMultipartHttpServletRequest;
import org.springframework.web.multipart.support.DefaultMultipartHttpServletRequest;
import org.springframework.web.util.WebUtils;

/**
 * 用于分段文件上传
 * Servlet-based {@link MultipartResolver} implementation for
 * <a href="https://commons.apache.org/proper/commons-fileupload">Apache Commons FileUpload</a>
 * 1.2 or above.
 *
 * <p>Provides "maxUploadSize", "maxInMemorySize" and "defaultEncoding" settings as
 * bean properties (inherited from {@link CommonsFileUploadSupport}). See corresponding
 * ServletFileUpload / DiskFileItemFactory properties ("sizeMax", "sizeThreshold",
 * "headerEncoding") for details in terms of defaults and accepted values.
 *
 * <p>Saves temporary files to the servlet container's temporary directory.
 * Needs to be initialized <i>either</i> by an application context <i>or</i>
 * via the constructor that takes a ServletContext (for standalone usage).
 *
 * @author Trevor D. Cook
 * @author Juergen Hoeller
 * @since 29.09.2003
 * @see #CommonsMultipartResolver(ServletContext)
 * @see #setResolveLazily
 * @see org.apache.commons.fileupload.servlet.ServletFileUpload
 * @see org.apache.commons.fileupload.disk.DiskFileItemFactory
 */
public class CommonsMultipartResolver extends CommonsFileUploadSupport
		implements MultipartResolver, ServletContextAware {

	/**
	 * 是否延迟解析
	 */
	private boolean resolveLazily = false;


	/**
	 * Constructor for use as bean. Determines the servlet container's
	 * temporary directory via the ServletContext passed in as through the
	 * ServletContextAware interface (typically by a WebApplicationContext).
	 * @see #setServletContext
	 * @see org.springframework.web.context.ServletContextAware
	 * @see org.springframework.web.context.WebApplicationContext
	 */
	public CommonsMultipartResolver() {
		super();
	}

	/**
	 * Constructor for standalone usage. Determines the servlet container's
	 * temporary directory via the given ServletContext.
	 * @param servletContext the ServletContext to use
	 */
	public CommonsMultipartResolver(ServletContext servletContext) {
		this();
		setServletContext(servletContext);
	}


	/**
	 * 设置是否在文件或参数访问时延迟解决多部分请求。
	 * Set whether to resolve the multipart request lazily at the time of
	 * file or parameter access.
	 * 默认为false，立即解析分段元素，并且在调用resolveMultipart时抛出对应异常
	 * <p>Default is "false", resolving the multipart elements immediately, throwing
	 * corresponding exceptions at the time of the {@link #resolveMultipart} call.
	 * 设置为true，延迟解析分段元素，在获取分段文件或者参数是抛出解析异常
	 * Switch this to "true" for lazy multipart parsing, throwing parse exceptions
	 * once the application attempts to obtain multipart files or parameters.
	 */
	public void setResolveLazily(boolean resolveLazily) {
		this.resolveLazily = resolveLazily;
	}

	/**
	 * 将给定的阿帕奇规范文件上传实例包装为ServletFileUpload对象，
	 * 可以覆盖以使用自定义子类，例如用于测试目的。
	 * Initialize the underlying {@code org.apache.commons.fileupload.servlet.ServletFileUpload}
	 * instance. Can be overridden to use a custom subclass, e.g. for testing purposes.
	 * @param fileItemFactory the Commons FileItemFactory to use
	 * @return the new ServletFileUpload instance
	 */
	@Override
	protected FileUpload newFileUpload(FileItemFactory fileItemFactory) {
		return new ServletFileUpload(fileItemFactory);
	}

	@Override
	public void setServletContext(ServletContext servletContext) {
		//如果使用临时目录上传文件
		if (!isUploadTempDirSpecified()) {
			//从servlet上下文中获取临时文件目录，并且设置到文件管理器中
			getFileItemFactory().setRepository(WebUtils.getTempDir(servletContext));
		}
	}


	@Override
	public boolean isMultipart(HttpServletRequest request) {
		//使用阿帕奇包判断是不是分段上传文件的请求
		return ServletFileUpload.isMultipartContent(request);
	}

	@Override
	public MultipartHttpServletRequest resolveMultipart(final HttpServletRequest request) throws MultipartException {
		Assert.notNull(request, "Request must not be null");
		//如果延迟加载
		if (this.resolveLazily) {
			//包装HttpServletRequest请求
			return new DefaultMultipartHttpServletRequest(request) {
				@Override
				protected void initializeMultipart() {
					//解析请求
					MultipartParsingResult parsingResult = parseRequest(request);
					//从解析的结果中获取解析的文件
					setMultipartFiles(parsingResult.getMultipartFiles());
					//从解析的结果中获取解析的表单参数
					setMultipartParameters(parsingResult.getMultipartParameters());
					//从解析的结果中获取解析的表单编码
					setMultipartParameterContentTypes(parsingResult.getMultipartParameterContentTypes());
				}
			};
		}
		else {
			//立即加载，解析请求
			MultipartParsingResult parsingResult = parseRequest(request);
			return new DefaultMultipartHttpServletRequest(request, parsingResult.getMultipartFiles(),
					parsingResult.getMultipartParameters(), parsingResult.getMultipartParameterContentTypes());
		}
	}

	/**
	 * 解析给定的servlet请求，解析其多段元素。
	 * Parse the given servlet request, resolving its multipart elements.
	 * @param request the request to parse
	 * @return the parsing result
	 * @throws MultipartException if multipart resolution failed.
	 */
	protected MultipartParsingResult parseRequest(HttpServletRequest request) throws MultipartException {
		//解析编码
		String encoding = determineEncoding(request);
		//检查指定编码并且返回FileUpload
		FileUpload fileUpload = prepareFileUpload(encoding);
		try {
			//使用阿帕奇的api解析请求的表单参数或者文件
			List<FileItem> fileItems = ((ServletFileUpload) fileUpload).parseRequest(request);
			//将给定的阿帕奇Commons FileItems列表解析为Spring MultipartParsingResult
			return parseFileItems(fileItems, encoding);
		}
		catch (FileUploadBase.SizeLimitExceededException ex) {
			throw new MaxUploadSizeExceededException(fileUpload.getSizeMax(), ex);
		}
		catch (FileUploadBase.FileSizeLimitExceededException ex) {
			throw new MaxUploadSizeExceededException(fileUpload.getFileSizeMax(), ex);
		}
		catch (FileUploadException ex) {
			throw new MultipartException("Failed to parse multipart servlet request", ex);
		}
	}

	/**
	 * 确定给定请求的编码。 可以在子类中覆盖。
	 * Determine the encoding for the given request.
	 * Can be overridden in subclasses.
	 * 默认实现检查heep请求编码，找不到则使用默认编码。
	 * <p>The default implementation checks the request encoding,
	 * falling back to the default encoding specified for this resolver.
	 * @param request current HTTP request
	 * @return the encoding for the request (never {@code null})
	 * @see javax.servlet.ServletRequest#getCharacterEncoding
	 * @see #setDefaultEncoding
	 */
	protected String determineEncoding(HttpServletRequest request) {
		String encoding = request.getCharacterEncoding();
		if (encoding == null) {
			encoding = getDefaultEncoding();
		}
		return encoding;
	}

	@Override
	public void cleanupMultipart(MultipartHttpServletRequest request) {
		//如果不是实现自AbstractMultipartHttpServletRequest），
		//或者请求已经解析过
		if (!(request instanceof AbstractMultipartHttpServletRequest) ||
				((AbstractMultipartHttpServletRequest) request).isResolved()) {
			try {
				cleanupFileItems(request.getMultiFileMap());
			}
			catch (Throwable ex) {
				logger.warn("Failed to perform multipart cleanup for servlet request", ex);
			}
		}
	}

}
