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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.xml.internal.bind.v2.TODO;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.io.Resource;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.WebUtils;

/**
 * Base class for multipart resolvers that use Apache Commons FileUpload
 * 1.2 or above.
 *
 * <p>Provides common configuration properties and parsing functionality
 * for multipart requests, using a Map of Spring CommonsMultipartFile instances
 * as representation of uploaded files and a String-based parameter Map as
 * representation of uploaded form fields.
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see CommonsMultipartFile
 * @see CommonsMultipartResolver
 */
public abstract class CommonsFileUploadSupport {

	protected final Log logger = LogFactory.getLog(getClass());

	private final DiskFileItemFactory fileItemFactory;

	private final FileUpload fileUpload;

	private boolean uploadTempDirSpecified = false;

	/**
	 * 是否保留客户端传递的保存文件路径
	 */
	private boolean preserveFilename = false;


	/**
	 * Instantiate a new CommonsFileUploadSupport with its
	 * corresponding FileItemFactory and FileUpload instances.
	 * @see #newFileItemFactory
	 * @see #newFileUpload
	 */
	public CommonsFileUploadSupport() {
		this.fileItemFactory = newFileItemFactory();
		this.fileUpload = newFileUpload(getFileItemFactory());
	}


	/**
	 * Return the underlying {@code org.apache.commons.fileupload.disk.DiskFileItemFactory}
	 * instance. There is hardly any need to access this.
	 * @return the underlying DiskFileItemFactory instance
	 */
	public DiskFileItemFactory getFileItemFactory() {
		return this.fileItemFactory;
	}

	/**
	 * Return the underlying {@code org.apache.commons.fileupload.FileUpload}
	 * instance. There is hardly any need to access this.
	 * @return the underlying FileUpload instance
	 */
	public FileUpload getFileUpload() {
		return this.fileUpload;
	}

	/**
	 * Set the maximum allowed size (in bytes) before an upload gets rejected.
	 * -1 indicates no limit (the default).
	 * @param maxUploadSize the maximum upload size allowed
	 * @see org.apache.commons.fileupload.FileUploadBase#setSizeMax
	 */
	public void setMaxUploadSize(long maxUploadSize) {
		this.fileUpload.setSizeMax(maxUploadSize);
	}

	/**
	 * Set the maximum allowed size (in bytes) for each individual file before
	 * an upload gets rejected. -1 indicates no limit (the default).
	 * @param maxUploadSizePerFile the maximum upload size per file
	 * @since 4.2
	 * @see org.apache.commons.fileupload.FileUploadBase#setFileSizeMax
	 */
	public void setMaxUploadSizePerFile(long maxUploadSizePerFile) {
		this.fileUpload.setFileSizeMax(maxUploadSizePerFile);
	}

	/**
	 * Set the maximum allowed size (in bytes) before uploads are written to disk.
	 * Uploaded files will still be received past this amount, but they will not be
	 * stored in memory. Default is 10240, according to Commons FileUpload.
	 * @param maxInMemorySize the maximum in memory size allowed
	 * @see org.apache.commons.fileupload.disk.DiskFileItemFactory#setSizeThreshold
	 */
	public void setMaxInMemorySize(int maxInMemorySize) {
		this.fileItemFactory.setSizeThreshold(maxInMemorySize);
	}

	/**
	 * Set the default character encoding to use for parsing requests,
	 * to be applied to headers of individual parts and to form fields.
	 * Default is ISO-8859-1, according to the Servlet spec.
	 * <p>If the request specifies a character encoding itself, the request
	 * encoding will override this setting. This also allows for generically
	 * overriding the character encoding in a filter that invokes the
	 * {@code ServletRequest.setCharacterEncoding} method.
	 * @param defaultEncoding the character encoding to use
	 * @see javax.servlet.ServletRequest#getCharacterEncoding
	 * @see javax.servlet.ServletRequest#setCharacterEncoding
	 * @see WebUtils#DEFAULT_CHARACTER_ENCODING
	 * @see org.apache.commons.fileupload.FileUploadBase#setHeaderEncoding
	 */
	public void setDefaultEncoding(String defaultEncoding) {
		this.fileUpload.setHeaderEncoding(defaultEncoding);
	}

	/**
	 * Determine the default encoding to use for parsing requests.
	 * @see #setDefaultEncoding
	 */
	protected String getDefaultEncoding() {
		String encoding = getFileUpload().getHeaderEncoding();
		if (encoding == null) {
			encoding = WebUtils.DEFAULT_CHARACTER_ENCODING;
		}
		return encoding;
	}

	/**
	 * Set the temporary directory where uploaded files get stored.
	 * Default is the servlet container's temporary directory for the web application.
	 * @see org.springframework.web.util.WebUtils#TEMP_DIR_CONTEXT_ATTRIBUTE
	 */
	public void setUploadTempDir(Resource uploadTempDir) throws IOException {
		if (!uploadTempDir.exists() && !uploadTempDir.getFile().mkdirs()) {
			throw new IllegalArgumentException("Given uploadTempDir [" + uploadTempDir + "] could not be created");
		}
		this.fileItemFactory.setRepository(uploadTempDir.getFile());
		this.uploadTempDirSpecified = true;
	}

	/**
	 * 返回存储上传文件的临时目录
	 * Return the temporary directory where uploaded files get stored.
	 * @see #setUploadTempDir
	 */
	protected boolean isUploadTempDirSpecified() {
		return this.uploadTempDirSpecified;
	}

	/**
	 * Set whether to preserve the filename as sent by the client, not stripping off
	 * path information in {@link CommonsMultipartFile#getOriginalFilename()}.
	 * <p>Default is "false", stripping off path information that may prefix the
	 * actual filename e.g. from Opera. Switch this to "true" for preserving the
	 * client-specified filename as-is, including potential path separators.
	 * @since 4.3.5
	 * @see MultipartFile#getOriginalFilename()
	 * @see CommonsMultipartFile#setPreserveFilename(boolean)
	 */
	public void setPreserveFilename(boolean preserveFilename) {
		this.preserveFilename = preserveFilename;
	}


	/**
	 * Factory method for a Commons DiskFileItemFactory instance.
	 * <p>Default implementation returns a standard DiskFileItemFactory.
	 * Can be overridden to use a custom subclass, e.g. for testing purposes.
	 * @return the new DiskFileItemFactory instance
	 */
	protected DiskFileItemFactory newFileItemFactory() {
		return new DiskFileItemFactory();
	}

	/**
	 * Factory method for a Commons FileUpload instance.
	 * <p><b>To be implemented by subclasses.</b>
	 * @param fileItemFactory the Commons FileItemFactory to build upon
	 * @return the Commons FileUpload instance
	 */
	protected abstract FileUpload newFileUpload(FileItemFactory fileItemFactory);


	/**
	 * 用给定的编码集确定适当的FileUpload实例。
	 * Determine an appropriate FileUpload instance for the given encoding.
	 * 如果编码匹配，则默认实现返回共享的FileUpload实例
	 * 否则，以所需的编码以外的其他配置创建一个新的FileUpload实例。
	 * <p>Default implementation returns the shared FileUpload instance
	 * if the encoding matches, else creates a new FileUpload instance
	 * with the same configuration other than the desired encoding.
	 * @param encoding the character encoding to use
	 * @return an appropriate FileUpload instance.
	 */
	protected FileUpload prepareFileUpload(@Nullable String encoding) {
		//获取上传文件
		FileUpload fileUpload = getFileUpload();
		//创建一个实际上传文件副本
		FileUpload actualFileUpload = fileUpload;

		//如果请求指定自己的编码与默认编码不匹配，则使用新的临时FileUpload实例。
		// Use new temporary FileUpload instance if the request specifies
		// its own encoding that does not match the default encoding.
		//指定了编码并且和阿帕奇请求里的不一样
		if (encoding != null && !encoding.equals(fileUpload.getHeaderEncoding())) {
			//覆盖成一个新的FileUpload，参数
			actualFileUpload = newFileUpload(getFileItemFactory());
			actualFileUpload.setSizeMax(fileUpload.getSizeMax());
			actualFileUpload.setFileSizeMax(fileUpload.getFileSizeMax());
			actualFileUpload.setHeaderEncoding(encoding);
		}

		return actualFileUpload;
	}

	/**
	 * 将给定的阿帕奇Commons FileItems列表解析为Spring MultipartParsingResult，
	 * Parse the given List of Commons FileItems into a Spring MultipartParsingResult,
	 * 包含Spring MultipartFile实例和一个multipart参数映射。
	 * containing Spring MultipartFile instances and a Map of multipart parameter.
	 * @param fileItems the Commons FileItems to parse
	 * @param encoding the encoding to use for form fields
	 * @return the Spring MultipartParsingResult
	 * @see CommonsMultipartFile#CommonsMultipartFile(org.apache.commons.fileupload.FileItem)
	 */
	protected MultipartParsingResult parseFileItems(List<FileItem> fileItems, String encoding) {
		//分段文件名和文件的映射
		MultiValueMap<String, MultipartFile> multipartFiles = new LinkedMultiValueMap<>();
		//表单属性键和值的映射
		Map<String, String[]> multipartParameters = new HashMap<>();
		//表单属性键和编码的映射
		Map<String, String> multipartParameterContentTypes = new HashMap<>();

		// 提取分段提交文件和表单参数。
		// Extract multipart files and multipart parameters.
		for (FileItem fileItem : fileItems) {
			//如果是简单表单字段
			if (fileItem.isFormField()) {
				String value;
				//确定编码
				String partEncoding = determineEncoding(fileItem.getContentType(), encoding);
				try {
					//通过制定编码拿到具体表单
					value = fileItem.getString(partEncoding);
				}
				catch (UnsupportedEncodingException ex) {
					if (logger.isWarnEnabled()) {
						logger.warn("Could not decode multipart item '" + fileItem.getFieldName() +
								"' with encoding '" + partEncoding + "': using platform default");
					}
					//默认输出值为字符串类型
					value = fileItem.getString();
				}
				//获得属性名
				String[] curParam = multipartParameters.get(fileItem.getFieldName());
				if (curParam == null) {
					//放置简单表单字段
					// simple form field
					//记录映射
					multipartParameters.put(fileItem.getFieldName(), new String[] {value});
				}
				else {
					//放置简单数组字段
					// array of simple form fields
					//记录映射
					String[] newParam = StringUtils.addStringToArray(curParam, value);
					multipartParameters.put(fileItem.getFieldName(), newParam);
				}
				//记录映射
				multipartParameterContentTypes.put(fileItem.getFieldName(), fileItem.getContentType());
			}
			else {
				//分段文件字段
				// multipart file field
				//将阿帕奇的FileItem包装成spring的CommonsMultipartFile
				CommonsMultipartFile file = createMultipartFile(fileItem);
				//记录映射
				multipartFiles.add(file.getName(), file);
				LogFormatUtils.traceDebug(logger, traceOn ->
						"Part '" + file.getName() + "', size " + file.getSize() +
								" bytes, filename='" + file.getOriginalFilename() + "'" +
								(traceOn ? ", storage=" + file.getStorageDescription() : "")
				);
			}
		}
		return new MultipartParsingResult(multipartFiles, multipartParameters, multipartParameterContentTypes);
	}

	/**
	 * 将阿帕奇的FileItem包装成spring的CommonsMultipartFile
	 * Create a {@link CommonsMultipartFile} wrapper for the given Commons {@link FileItem}.
	 * @param fileItem the Commons FileItem to wrap
	 * @return the corresponding CommonsMultipartFile (potentially a custom subclass)
	 * @since 4.3.5
	 * @see #setPreserveFilename(boolean)
	 * @see CommonsMultipartFile#setPreserveFilename(boolean)
	 */
	protected CommonsMultipartFile createMultipartFile(FileItem fileItem) {
		CommonsMultipartFile multipartFile = new CommonsMultipartFile(fileItem);
		//设置是否保留该文件设置的上传路径
		multipartFile.setPreserveFilename(this.preserveFilename);
		return multipartFile;
	}

	/**
	 * 清理在分段解析过程中创建的Spring MultipartFiles，
	 * Cleanup the Spring MultipartFiles created during multipart parsing,
	 * 可能将临时数据保存在磁盘上。
	 * potentially holding temporary data on disk.
	 * <p>Deletes the underlying Commons FileItem instances.
	 * @param multipartFiles a Collection of MultipartFile instances
	 * @see org.apache.commons.fileupload.FileItem#delete()
	 */
	protected void cleanupFileItems(MultiValueMap<String, MultipartFile> multipartFiles) {
		//循环处理request请求中的所有文件
		for (List<MultipartFile> files : multipartFiles.values()) {
			for (MultipartFile file : files) {
				if (file instanceof CommonsMultipartFile) {
					CommonsMultipartFile cmf = (CommonsMultipartFile) file;
					//获取他的文件符并进行删除
					cmf.getFileItem().delete();
					LogFormatUtils.traceDebug(logger, traceOn ->
							"Cleaning up part '" + cmf.getName() +
									"', filename '" + cmf.getOriginalFilename() + "'" +
									(traceOn ? ", stored " + cmf.getStorageDescription() : ""));
				}
			}
		}
	}

	private String determineEncoding(String contentTypeHeader, String defaultEncoding) {
		if (!StringUtils.hasText(contentTypeHeader)) {
			return defaultEncoding;
		}
		MediaType contentType = MediaType.parseMediaType(contentTypeHeader);
		Charset charset = contentType.getCharset();
		return (charset != null ? charset.name() : defaultEncoding);
	}


	/**
	 * Holder for a Map of Spring MultipartFiles and a Map of
	 * multipart parameters.
	 */
	protected static class MultipartParsingResult {

		/**
		 * 文件名和文件的映射
		 */
		private final MultiValueMap<String, MultipartFile> multipartFiles;

		/**
		 * 表单键和值的映射
		 */
		private final Map<String, String[]> multipartParameters;

		/**
		 * 表单键和编码的映射
		 */
		private final Map<String, String> multipartParameterContentTypes;

		public MultipartParsingResult(MultiValueMap<String, MultipartFile> mpFiles,
				Map<String, String[]> mpParams, Map<String, String> mpParamContentTypes) {

			this.multipartFiles = mpFiles;
			this.multipartParameters = mpParams;
			this.multipartParameterContentTypes = mpParamContentTypes;
		}

		public MultiValueMap<String, MultipartFile> getMultipartFiles() {
			return this.multipartFiles;
		}

		public Map<String, String[]> getMultipartParameters() {
			return this.multipartParameters;
		}

		public Map<String, String> getMultipartParameterContentTypes() {
			return this.multipartParameterContentTypes;
		}
	}

}
