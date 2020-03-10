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

package org.springframework.web.servlet.i18n;

import java.util.Locale;
import java.util.TimeZone;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.context.i18n.TimeZoneAwareLocaleContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleContextResolver;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.util.CookieGenerator;
import org.springframework.web.util.WebUtils;

/**
 * LocaleResolver国际化解析器实现，在自定义设置的情况下，使用发送回用户的cookie，
 * 并回退到指定的默认区域设置或请求的接受头区域设置。
 * {@link LocaleResolver} implementation that uses a cookie sent back to the user
 * in case of a custom setting, with a fallback to the specified default locale
 * or the request's accept-header locale.
 *
 * 这对于没有用户会话的无状态应用程序特别有用。cookie也可以选择性地包含关联的时区值；或者，可以指定默认时区。
 * <p>This is particularly useful for stateless applications without user sessions.
 * The cookie may optionally contain an associated time zone value as well;
 * alternatively, you may specify a default time zone.
 *
 * 自定义控制器可以通过调用解析器上的setLocale（Context）覆盖用户的区域设置和时区，
 * 例如，响应区域设置更改请求。作为一个更方便的选择，可以使用changeLocale
 * <p>Custom controllers can override the user's locale and time zone by calling
 * {@code #setLocale(Context)} on the resolver, e.g. responding to a locale change
 * request. As a more convenient alternative, consider using
 * {@link org.springframework.web.servlet.support.RequestContext#changeLocale}.
 *
 * @author Juergen Hoeller
 * @author Jean-Pierre Pawlak
 * @since 27.02.2003
 * @see #setDefaultLocale
 * @see #setDefaultTimeZone
 */
public class CookieLocaleResolver extends CookieGenerator implements LocaleContextResolver {

	/**
	 * 需要解析request里的属性名称
	 * The name of the request attribute that holds the {@code Locale}.
	 * <p>Only used for overriding a cookie value if the locale has been
	 * changed in the course of the current request!
	 * <p>Use {@code RequestContext(Utils).getLocale()}
	 * to retrieve the current locale in controllers or views.
	 * @see org.springframework.web.servlet.support.RequestContext#getLocale
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getLocale
	 */
	public static final String LOCALE_REQUEST_ATTRIBUTE_NAME = CookieLocaleResolver.class.getName() + ".LOCALE";

	/**
	 * The name of the request attribute that holds the {@code TimeZone}.
	 * <p>Only used for overriding a cookie value if the locale has been
	 * changed in the course of the current request!
	 * <p>Use {@code RequestContext(Utils).getTimeZone()}
	 * to retrieve the current time zone in controllers or views.
	 * @see org.springframework.web.servlet.support.RequestContext#getTimeZone
	 * @see org.springframework.web.servlet.support.RequestContextUtils#getTimeZone
	 */
	public static final String TIME_ZONE_REQUEST_ATTRIBUTE_NAME = CookieLocaleResolver.class.getName() + ".TIME_ZONE";

	/**
	 * The default cookie name used if none is explicitly set.
	 */
	public static final String DEFAULT_COOKIE_NAME = CookieLocaleResolver.class.getName() + ".LOCALE";


	private boolean languageTagCompliant = true;

	private boolean rejectInvalidCookies = true;

	@Nullable
	private Locale defaultLocale;

	@Nullable
	private TimeZone defaultTimeZone;


	/**
	 * Create a new instance of the {@link CookieLocaleResolver} class
	 * using the {@link #DEFAULT_COOKIE_NAME default cookie name}.
	 */
	public CookieLocaleResolver() {
		setCookieName(DEFAULT_COOKIE_NAME);
	}


	/**
	 * 指定此解析程序的cookie是否应符合BCP 47语言标记，而不是Java的传统语言环境规范格式。
	 * Specify whether this resolver's cookies should be compliant with BCP 47
	 * language tags instead of Java's legacy locale specification format.
	 * <p>The default is {@code true}, as of 5.1. Switch this to {@code false}
	 * for rendering Java's legacy locale specification format. For parsing,
	 * this resolver leniently accepts the legacy {@link Locale#toString}
	 * format as well as BCP 47 language tags in any case.
	 * @since 4.3
	 * @see #parseLocaleValue(String)
	 * @see #toLocaleValue(Locale)
	 * @see Locale#forLanguageTag(String)
	 * @see Locale#toLanguageTag()
	 */
	public void setLanguageTagCompliant(boolean languageTagCompliant) {
		this.languageTagCompliant = languageTagCompliant;
	}

	/**
	 * 返回此解析程序的cookie是否应符合BCP 47语言标记，而不是Java的传统语言环境规范格式。
	 * Return whether this resolver's cookies should be compliant with BCP 47
	 * language tags instead of Java's legacy locale specification format.
	 * @since 4.3
	 */
	public boolean isLanguageTagCompliant() {
		return this.languageTagCompliant;
	}

	/**
	 * 指定是否拒绝包含无效内容（例如无效格式）的Cookie。
	 * Specify whether to reject cookies with invalid content (e.g. invalid format).
	 * 默认值是{@code true}。关闭此选项可对解析失败进行宽大处理，在这种情况下会返回到默认的区域设置和时区。
	 * <p>The default is {@code true}. Turn this off for lenient handling of parse
	 * failures, falling back to the default locale and time zone in such a case.
	 * @since 5.1.7
	 * @see #setDefaultLocale
	 * @see #setDefaultTimeZone
	 * @see #determineDefaultLocale
	 * @see #determineDefaultTimeZone
	 */
	public void setRejectInvalidCookies(boolean rejectInvalidCookies) {
		this.rejectInvalidCookies = rejectInvalidCookies;
	}

	/**
	 * 返回是否拒绝包含无效内容（例如无效格式）的Cookie。
	 * Return whether to reject cookies with invalid content (e.g. invalid format).
	 * @since 5.1.7
	 */
	public boolean isRejectInvalidCookies() {
		return this.rejectInvalidCookies;
	}

	/**
	 * 设置一个固定的默认区域设置，如果找不到cookie，此解析程序将返回该区域设置。
	 * Set a fixed locale that this resolver will return if no cookie found.
	 */
	public void setDefaultLocale(@Nullable Locale defaultLocale) {
		this.defaultLocale = defaultLocale;
	}

	/**
	 * 返回固定的默认区域设置，如果找不到cookie，此解析程序将返回该区域设置。
	 * Return the fixed locale that this resolver will return if no cookie found,
	 * if any.
	 */
	@Nullable
	protected Locale getDefaultLocale() {
		return this.defaultLocale;
	}

	/**
	 * 设置一个固定时区，如果找不到cookie，此解析程序将返回该时区。
	 * Set a fixed time zone that this resolver will return if no cookie found.
	 * @since 4.0
	 */
	public void setDefaultTimeZone(@Nullable TimeZone defaultTimeZone) {
		this.defaultTimeZone = defaultTimeZone;
	}

	/**
	 * 返回一个固定时区，如果找不到cookie，此解析程序将返回该时区。
	 * Return the fixed time zone that this resolver will return if no cookie found,
	 * if any.
	 * @since 4.0
	 */
	@Nullable
	protected TimeZone getDefaultTimeZone() {
		return this.defaultTimeZone;
	}


	@Override
	public Locale resolveLocale(HttpServletRequest request) {
		//如果请求中不包含时区信息，则在本地cookie中进行解析
		parseLocaleCookieIfNecessary(request);
		//返回解析后在地区
		return (Locale) request.getAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME);
	}

	@Override
	public LocaleContext resolveLocaleContext(final HttpServletRequest request) {
		parseLocaleCookieIfNecessary(request);
		return new TimeZoneAwareLocaleContext() {
			@Override
			@Nullable
			public Locale getLocale() {
				return (Locale) request.getAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME);
			}
			@Override
			@Nullable
			public TimeZone getTimeZone() {
				return (TimeZone) request.getAttribute(TIME_ZONE_REQUEST_ATTRIBUTE_NAME);
			}
		};
	}

	/**
	 * 如果请求中获取不到需要解析的属性，则在本地cookie中进行解析
	 * @param request
	 */
	private void parseLocaleCookieIfNecessary(HttpServletRequest request) {
		//请求中获取不到需要解析的属性
		if (request.getAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME) == null) {
			Locale locale = null;
			TimeZone timeZone = null;

			// 检索并分析本地cookie值。
			// Retrieve and parse cookie value.
			String cookieName = getCookieName();
			// 如果有本地cookie名
			if (cookieName != null) {
				//根据名称匹配并返回第一个cookie
				Cookie cookie = WebUtils.getCookie(request, cookieName);
				//如果获取到了对应的cookie
				if (cookie != null) {
					String value = cookie.getValue();
					String localePart = value;
					String timeZonePart = null;
					//兼容'/'
					int separatorIndex = localePart.indexOf('/');
					if (separatorIndex == -1) {
						//兼容空格
						// Leniently accept older cookies separated by a space...
						separatorIndex = localePart.indexOf(' ');
					}
					if (separatorIndex >= 0) {
						//解析地区
						localePart = value.substring(0, separatorIndex);
						//解析时区
						timeZonePart = value.substring(separatorIndex + 1);
					}
					try {
						//解析本地cookie里的区域属性
						locale = (!"-".equals(localePart) ? parseLocaleValue(localePart) : null);
						if (timeZonePart != null) {
							//解析时区
							timeZone = StringUtils.parseTimeZoneString(timeZonePart);
						}
					}
					catch (IllegalArgumentException ex) {
						//如果必须拒绝无效的cookie 并且 是请求中没有对应的时区属性，
						if (isRejectInvalidCookies() &&
								request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE) == null) {
							//抛出异常
							throw new IllegalStateException("Encountered invalid locale cookie '" +
									cookieName + "': [" + value + "] due to: " + ex.getMessage());
						}
						else {
							// 宽松处理（例如错误分派）：忽略区域设置/时区分析异常
							// Lenient handling (e.g. error dispatch): ignore locale/timezone parse exceptions
							if (logger.isDebugEnabled()) {
								logger.debug("Ignoring invalid locale cookie '" + cookieName +
										"': [" + value + "] due to: " + ex.getMessage());
							}
						}
					}
					if (logger.isTraceEnabled()) {
						logger.trace("Parsed cookie value [" + cookie.getValue() + "] into locale '" + locale +
								"'" + (timeZone != null ? " and time zone '" + timeZone.getID() + "'" : ""));
					}
				}
			}

			//设置区域到request里
			request.setAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME,
					(locale != null ? locale : determineDefaultLocale(request)));
			//设置时区到request里
			request.setAttribute(TIME_ZONE_REQUEST_ATTRIBUTE_NAME,
					(timeZone != null ? timeZone : determineDefaultTimeZone(request)));
		}
	}

	@Override
	public void setLocale(HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable Locale locale) {
		setLocaleContext(request, response, (locale != null ? new SimpleLocaleContext(locale) : null));
	}

	@Override
	public void setLocaleContext(HttpServletRequest request, @Nullable HttpServletResponse response,
			@Nullable LocaleContext localeContext) {

		Assert.notNull(response, "HttpServletResponse is required for CookieLocaleResolver");

		Locale locale = null;
		TimeZone timeZone = null;
		if (localeContext != null) {
			locale = localeContext.getLocale();
			if (localeContext instanceof TimeZoneAwareLocaleContext) {
				timeZone = ((TimeZoneAwareLocaleContext) localeContext).getTimeZone();
			}
			addCookie(response,
					(locale != null ? toLocaleValue(locale) : "-") + (timeZone != null ? '/' + timeZone.getID() : ""));
		}
		else {
			removeCookie(response);
		}
		request.setAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME,
				(locale != null ? locale : determineDefaultLocale(request)));
		request.setAttribute(TIME_ZONE_REQUEST_ATTRIBUTE_NAME,
				(timeZone != null ? timeZone : determineDefaultTimeZone(request)));
	}


	/**
	 * 分析来自本地cookie的给定区域设置值
	 * Parse the given locale value coming from an incoming cookie.
	 * <p>The default implementation calls {@link StringUtils#parseLocale(String)},
	 * accepting the {@link Locale#toString} format as well as BCP 47 language tags.
	 * @param localeValue the locale value to parse
	 * @return the corresponding {@code Locale} instance
	 * @since 4.3
	 * @see StringUtils#parseLocale(String)
	 */
	@Nullable
	protected Locale parseLocaleValue(String localeValue) {
		return StringUtils.parseLocale(localeValue);
	}

	/**
	 * Render the given locale as a text value for inclusion in a cookie.
	 * <p>The default implementation calls {@link Locale#toString()}
	 * or JDK 7's {@link Locale#toLanguageTag()}, depending on the
	 * {@link #setLanguageTagCompliant "languageTagCompliant"} configuration property.
	 * @param locale the locale to stringify
	 * @return a String representation for the given locale
	 * @since 4.3
	 * @see #isLanguageTagCompliant()
	 */
	protected String toLocaleValue(Locale locale) {
		return (isLanguageTagCompliant() ? locale.toLanguageTag() : locale.toString());
	}

	/**
	 * 使用默认区域设置，如果没有设置则调用request默认区域。
	 * Determine the default locale for the given request,
	 * Called if no locale cookie has been found.
	 * <p>The default implementation returns the specified default locale,
	 * if any, else falls back to the request's accept-header locale.
	 * @param request the request to resolve the locale for
	 * @return the default locale (never {@code null})
	 * @see #setDefaultLocale
	 * @see javax.servlet.http.HttpServletRequest#getLocale()
	 */
	@Nullable
	protected Locale determineDefaultLocale(HttpServletRequest request) {
		//获取默认区域
		Locale defaultLocale = getDefaultLocale();
		if (defaultLocale == null) {
			//获取请求里的区域
			defaultLocale = request.getLocale();
		}
		return defaultLocale;
	}

	/**
	 * 返回默认时区
	 * Determine the default time zone for the given request,
	 * Called if no time zone cookie has been found.
	 * <p>The default implementation returns the specified default time zone,
	 * if any, or {@code null} otherwise.
	 * @param request the request to resolve the time zone for
	 * @return the default time zone (or {@code null} if none defined)
	 * @see #setDefaultTimeZone
	 */
	@Nullable
	protected TimeZone determineDefaultTimeZone(HttpServletRequest request) {
		//返回默认时区
		return getDefaultTimeZone();
	}

}
