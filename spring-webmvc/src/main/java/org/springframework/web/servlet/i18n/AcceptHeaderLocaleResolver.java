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

package org.springframework.web.servlet.i18n;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;

/**
 * LocaleResolver只使用在HTTP请求的“accept language”头中指定的主语言环境的实现
 * （即客户端浏览器发送的语言环境，通常是客户端操作系统的语言环境）。
 * {@link LocaleResolver} implementation that simply uses the primary locale
 * specified in the "accept-language" header of the HTTP request (that is,
 * the locale sent by the client browser, normally that of the client's OS).
 *
 * 注意：不支持setLocale来改变区域设置，因为accept头只能通过更改客户端的区域设置来更改。
 * <p>Note: Does not support {@code setLocale}, since the accept header
 * can only be changed through changing the client's locale settings.
 *
 * @author Juergen Hoeller
 * @author Rossen Stoyanchev
 * @since 27.02.2003
 * @see javax.servlet.http.HttpServletRequest#getLocale()
 */
public class AcceptHeaderLocaleResolver implements LocaleResolver {

	/**
	 * 受支持区域设置列表
	 */
	private final List<Locale> supportedLocales = new ArrayList<>(4);

	/**
	 * 默认区域
	 */
	@Nullable
	private Locale defaultLocale;


	/**
	 * 配置支持的区域设置
	 * Configure supported locales to check against the requested locales
	 * determined via {@link HttpServletRequest#getLocales()}. If this is not
	 * configured then {@link HttpServletRequest#getLocale()} is used instead.
	 * @param locales the supported locales
	 * @since 4.3
	 */
	public void setSupportedLocales(List<Locale> locales) {
		//清空原有的配置
		this.supportedLocales.clear();
		//添加新的配置
		this.supportedLocales.addAll(locales);
	}

	/**
	 * 返回已配置的受支持区域设置列表。
	 * Return the configured list of supported locales.
	 * @since 4.3
	 */
	public List<Locale> getSupportedLocales() {
		return this.supportedLocales;
	}

	/**
	 * 配置一个固定的默认区域设置，如果请求没有“Accept Language”头，则返回该设置。
	 * Configure a fixed default locale to fall back on if the request does not
	 * have an "Accept-Language" header.
	 * <p>By default this is not set in which case when there is "Accept-Language"
	 * header, the default locale for the server is used as defined in
	 * {@link HttpServletRequest#getLocale()}.
	 * @param defaultLocale the default locale to use
	 * @since 4.3
	 */
	public void setDefaultLocale(@Nullable Locale defaultLocale) {
		this.defaultLocale = defaultLocale;
	}

	/**
	 * 配置的默认区域设置（如果有）。
	 * The configured default locale, if any.
	 * @since 4.3
	 */
	@Nullable
	public Locale getDefaultLocale() {
		return this.defaultLocale;
	}


	@Override
	public Locale resolveLocale(HttpServletRequest request) {
		//返回配置的默认区域设置
		Locale defaultLocale = getDefaultLocale();
		//如果设置了默认区域，并且请求中没有设置Accept-Language
		if (defaultLocale != null && request.getHeader("Accept-Language") == null) {
			//返回默认区域
			return defaultLocale;
		}
		//使用request里的区域
		Locale requestLocale = request.getLocale();
		//返回已配置的受支持区域设置列表
		List<Locale> supportedLocales = getSupportedLocales();
		//如果没有受支持区域 或者 受支持区域设置列表包含当前区域
		if (supportedLocales.isEmpty() || supportedLocales.contains(requestLocale)) {
			//选择使用request里的区域
			return requestLocale;
		}
		//匹配支持request的区域
		Locale supportedLocale = findSupportedLocale(request, supportedLocales);
		//如果能匹配到
		if (supportedLocale != null) {
			//使用能匹配request的受支持区域
			return supportedLocale;
		}
		//使用默认区域或者request里的区域
		return (defaultLocale != null ? defaultLocale : requestLocale);
	}

	/**
	 * 匹配支持request的区域
	 * @param request
	 * @param supportedLocales
	 * @return
	 */
	@Nullable
	private Locale findSupportedLocale(HttpServletRequest request, List<Locale> supportedLocales) {
		Enumeration<Locale> requestLocales = request.getLocales();
		Locale languageMatch = null;
		while (requestLocales.hasMoreElements()) {
			Locale locale = requestLocales.nextElement();
			if (supportedLocales.contains(locale)) {
				if (languageMatch == null || languageMatch.getLanguage().equals(locale.getLanguage())) {
					// 完全匹配：语言+国家，可能比以前的仅语言匹配缩小
					// Full match: language + country, possibly narrowed from earlier language-only match
					return locale;
				}
			}
			else if (languageMatch == null) {
				// 让我们试着找到一个只匹配一种语言作为后备
				// Let's try to find a language-only match as a fallback
				for (Locale candidate : supportedLocales) {
					if (!StringUtils.hasLength(candidate.getCountry()) &&
							candidate.getLanguage().equals(locale.getLanguage())) {
						languageMatch = candidate;
						break;
					}
				}
			}
		}
		return languageMatch;
	}

	@Override
	public void setLocale(HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable Locale locale) {
		throw new UnsupportedOperationException(
				"Cannot change HTTP accept header - use a different locale resolution strategy");
	}

}
