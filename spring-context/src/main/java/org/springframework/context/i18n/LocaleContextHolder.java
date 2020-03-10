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

package org.springframework.context.i18n;

import java.util.Locale;
import java.util.TimeZone;

import org.springframework.core.NamedInheritableThreadLocal;
import org.springframework.core.NamedThreadLocal;
import org.springframework.lang.Nullable;

/**
 * 将LocaleContext国家化区域上下文实例与当前线程关联的简单持有类。如果inheritable标志设置为true，
 * 则当前线程生成的任何子线程都将继承LocaleContext。
 * Simple holder class that associates a LocaleContext instance
 * with the current thread. The LocaleContext will be inherited
 * by any child threads spawned by the current thread if the
 * {@code inheritable} flag is set to {@code true}.
 *
 * 在Spring中用作当前区域设置的中心持有者
 * <p>Used as a central holder for the current Locale in Spring,
 * wherever necessary: for example, in MessageSourceAccessor.
 * DispatcherServlet automatically exposes its current Locale here.
 * Other applications can expose theirs too, to make classes like
 * MessageSourceAccessor automatically use that Locale.
 *
 * @author Juergen Hoeller
 * @author Nicholas Williams
 * @since 1.2
 * @see LocaleContext
 * @see org.springframework.context.support.MessageSourceAccessor
 * @see org.springframework.web.servlet.DispatcherServlet
 */
public final class LocaleContextHolder {

	/**
	 * 线程上下文变量
	 */
	private static final ThreadLocal<LocaleContext> localeContextHolder =
			new NamedThreadLocal<>("LocaleContext");

	/**
	 * 子线程上下文变量
	 */
	private static final ThreadLocal<LocaleContext> inheritableLocaleContextHolder =
			new NamedInheritableThreadLocal<>("LocaleContext");

	//在框架级别共享默认区域设置
	// Shared default locale at the framework level
	@Nullable
	private static Locale defaultLocale;

	//在框架级别共享默认时区设置
	// Shared default time zone at the framework level
	@Nullable
	private static TimeZone defaultTimeZone;


	private LocaleContextHolder() {
	}


	/**
	 * 重置当前线程的LocaleContext。
	 * Reset the LocaleContext for the current thread.
	 */
	public static void resetLocaleContext() {
		//清空线程上下文变量
		localeContextHolder.remove();
		//清空子线程上下文变量
		inheritableLocaleContextHolder.remove();
	}

	/**
	 * 将给定的LocaleContext区域环境与当前线程关联，
	 * Associate the given LocaleContext with the current thread,
	 * 不与其子线程关联
	 * <i>not</i> exposing it as inheritable for child threads.
	 * <p>The given LocaleContext may be a {@link TimeZoneAwareLocaleContext},
	 * containing a locale with associated time zone information.
	 * @param localeContext the current LocaleContext,
	 * or {@code null} to reset the thread-bound context
	 * @see SimpleLocaleContext
	 * @see SimpleTimeZoneAwareLocaleContext
	 */
	public static void setLocaleContext(@Nullable LocaleContext localeContext) {
		setLocaleContext(localeContext, false);
	}

	/**
	 * 将给定的LocaleContext区域环境与当前线程关联。
	 * Associate the given LocaleContext with the current thread.
	 * <p>The given LocaleContext may be a {@link TimeZoneAwareLocaleContext},
	 * containing a locale with associated time zone information.
	 * @param localeContext the current LocaleContext,
	 * or {@code null} to reset the thread-bound context
	 * @param inheritable whether to expose the LocaleContext as inheritable
	 * for child threads (using an {@link InheritableThreadLocal})
	 * @see SimpleLocaleContext
	 * @see SimpleTimeZoneAwareLocaleContext
	 */
	public static void setLocaleContext(@Nullable LocaleContext localeContext, boolean inheritable) {
		//如果当前区域环境数据为空，重置上下文
		if (localeContext == null) {
			resetLocaleContext();
		}
		else {
			//如果区域环境需要和子线程关联
			if (inheritable) {
				//设置到子线程的线程变量中
				inheritableLocaleContextHolder.set(localeContext);
				//从当前线程中移除
				localeContextHolder.remove();
			}
			else {
				//如果设置到当前线程中
				//设置到当前上下文中
				localeContextHolder.set(localeContext);
				//从子线程上下文中移除
				inheritableLocaleContextHolder.remove();
			}
		}
	}

	/**
	 * 返回与当前线程关联的LocaleContext区域环境（如果有）。
	 * Return the LocaleContext associated with the current thread, if any.
	 * @return the current LocaleContext, or {@code null} if none
	 */
	@Nullable
	public static LocaleContext getLocaleContext() {
		//先在当前线程变量中获取
		LocaleContext localeContext = localeContextHolder.get();
		if (localeContext == null) {
			//获取不到，尝试从子线程变量中获取
			localeContext = inheritableLocaleContextHolder.get();
		}
		return localeContext;
	}

	/**
	 * 将给定的区域设置与当前线程关联，保留可能已设置的任何时区。
	 * Associate the given Locale with the current thread,
	 * preserving any TimeZone that may have been set already.
	 * <p>Will implicitly create a LocaleContext for the given Locale,
	 * <i>not</i> exposing it as inheritable for child threads.
	 * @param locale the current Locale, or {@code null} to reset
	 * the locale part of thread-bound context
	 * @see #setTimeZone(TimeZone)
	 * @see SimpleLocaleContext#SimpleLocaleContext(Locale)
	 */
	public static void setLocale(@Nullable Locale locale) {
		setLocale(locale, false);
	}

	/**
	 * 将给定的区域设置与当前线程关联，并且保留可能已设置的任何时区。
	 * Associate the given Locale with the current thread,
	 * preserving any TimeZone that may have been set already.
	 * 将隐式创建给定区域设置的LocaleContext区域环境。
	 * <p>Will implicitly create a LocaleContext for the given Locale.
	 * @param locale the current Locale, or {@code null} to reset
	 * the locale part of thread-bound context
	 * @param inheritable whether to expose the LocaleContext as inheritable
	 * for child threads (using an {@link InheritableThreadLocal})
	 * @see #setTimeZone(TimeZone, boolean)
	 * @see SimpleLocaleContext#SimpleLocaleContext(Locale)
	 */
	public static void setLocale(@Nullable Locale locale, boolean inheritable) {
		//返回与当前线程关联的LocaleContext区域环境
		LocaleContext localeContext = getLocaleContext();
		//尝试解析当前区域中包含的时区信息
		TimeZone timeZone = (localeContext instanceof TimeZoneAwareLocaleContext ?
				((TimeZoneAwareLocaleContext) localeContext).getTimeZone() : null);
		if (timeZone != null) {
			//不能解析时区，就从区域环境中获取并包装
			localeContext = new SimpleTimeZoneAwareLocaleContext(locale, timeZone);
		}
		else if (locale != null) {
			localeContext = new SimpleLocaleContext(locale);
		}
		else {
			localeContext = null;
		}
		//将给定的LocaleContext区域环境与当前线程关联
		setLocaleContext(localeContext, inheritable);
	}

	/**
	 * 在框架级别设置一个共享的默认地区环境，作为JVM范围内默认地区环境的替代。
	 * Set a shared default locale at the framework level,
	 * as an alternative to the JVM-wide default locale.
	 * <p><b>NOTE:</b> This can be useful to set an application-level
	 * default locale which differs from the JVM-wide default locale.
	 * However, this requires each such application to operate against
	 * locally deployed Spring Framework jars. Do not deploy Spring
	 * as a shared library at the server level in such a scenario!
	 * @param locale the default locale (or {@code null} for none,
	 * letting lookups fall back to {@link Locale#getDefault()})
	 * @since 4.3.5
	 * @see #getLocale()
	 * @see Locale#getDefault()
	 */
	public static void setDefaultLocale(@Nullable Locale locale) {
		LocaleContextHolder.defaultLocale = locale;
	}

	/**
	 * 返回与当前线程关联的区域设置，如果不存在则返回默认的区域设置
	 * Return the Locale associated with the current thread, if any,
	 * or the system default Locale otherwise. This is effectively a
	 * replacement for {@link java.util.Locale#getDefault()},
	 * able to optionally respect a user-level Locale setting.
	 * <p>Note: This method has a fallback to the shared default Locale,
	 * either at the framework level or at the JVM-wide system level.
	 * If you'd like to check for the raw LocaleContext content
	 * (which may indicate no specific locale through {@code null}, use
	 * {@link #getLocaleContext()} and call {@link LocaleContext#getLocale()}
	 * @return the current Locale, or the system default Locale if no
	 * specific Locale has been associated with the current thread
	 * @see #getLocaleContext()
	 * @see LocaleContext#getLocale()
	 * @see #setDefaultLocale(Locale)
	 * @see java.util.Locale#getDefault()
	 */
	public static Locale getLocale() {
		return getLocale(getLocaleContext());
	}

	/**
	 * 返回区域设置，如果不存在则返回默认的区域设置
	 * Return the Locale associated with the given user context, if any,
	 * or the system default Locale otherwise. This is effectively a
	 * replacement for {@link java.util.Locale#getDefault()},
	 * able to optionally respect a user-level Locale setting.
	 * @param localeContext the user-level locale context to check
	 * @return the current Locale, or the system default Locale if no
	 * specific Locale has been associated with the current thread
	 * @since 5.0
	 * @see #getLocale()
	 * @see LocaleContext#getLocale()
	 * @see #setDefaultLocale(Locale)
	 * @see java.util.Locale#getDefault()
	 */
	public static Locale getLocale(@Nullable LocaleContext localeContext) {
		if (localeContext != null) {
			//获取区域环境
			Locale locale = localeContext.getLocale();
			if (locale != null) {
				//如果有则返回
				return locale;
			}
		}
		//没有的话尝试从框架默认环境中获取，如果框架没有指定则返回区域默认值（可能来自于启动参数，os等）
		return (defaultLocale != null ? defaultLocale : Locale.getDefault());
	}

	/**
	 * 将给定时区与当前线程关联，保留可能已设置的任何区域设置。
	 * Associate the given TimeZone with the current thread,
	 * preserving any Locale that may have been set already.
	 * 将隐式创建给定区域设置的LocaleContext，而不是将其公开为子线程的可继承。
	 * <p>Will implicitly create a LocaleContext for the given Locale,
	 * <i>not</i> exposing it as inheritable for child threads.
	 * @param timeZone the current TimeZone, or {@code null} to reset
	 * the time zone part of the thread-bound context
	 * @see #setLocale(Locale)
	 * @see SimpleTimeZoneAwareLocaleContext#SimpleTimeZoneAwareLocaleContext(Locale, TimeZone)
	 */
	public static void setTimeZone(@Nullable TimeZone timeZone) {
		setTimeZone(timeZone, false);
	}

	/**
	 * 将给定时区与当前线程关联，保留可能已设置的任何区域设置。
	 * Associate the given TimeZone with the current thread,
	 * preserving any Locale that may have been set already.
	 * <p>Will implicitly create a LocaleContext for the given Locale.
	 * @param timeZone the current TimeZone, or {@code null} to reset
	 * the time zone part of the thread-bound context
	 * @param inheritable whether to expose the LocaleContext as inheritable
	 * for child threads (using an {@link InheritableThreadLocal})
	 * @see #setLocale(Locale, boolean)
	 * @see SimpleTimeZoneAwareLocaleContext#SimpleTimeZoneAwareLocaleContext(Locale, TimeZone)
	 */
	public static void setTimeZone(@Nullable TimeZone timeZone, boolean inheritable) {
		//返回与当前线程关联的LocaleContext区域环境
		LocaleContext localeContext = getLocaleContext();
		Locale locale = (localeContext != null ? localeContext.getLocale() : null);
		//包装区域和时区
		if (timeZone != null) {
			localeContext = new SimpleTimeZoneAwareLocaleContext(locale, timeZone);
		}
		else if (locale != null) {
			localeContext = new SimpleLocaleContext(locale);
		}
		else {
			localeContext = null;
		}
		//将给定的LocaleContext区域环境与线程关联，并指定是否由子线程继承
		setLocaleContext(localeContext, inheritable);
	}

	/**
	 * 在框架级别设置共享默认时区，作为JVM范围内默认时区的替代。
	 * Set a shared default time zone at the framework level,
	 * as an alternative to the JVM-wide default time zone.
	 * <p><b>NOTE:</b> This can be useful to set an application-level
	 * default time zone which differs from the JVM-wide default time zone.
	 * However, this requires each such application to operate against
	 * locally deployed Spring Framework jars. Do not deploy Spring
	 * as a shared library at the server level in such a scenario!
	 * @param timeZone the default time zone (or {@code null} for none,
	 * letting lookups fall back to {@link TimeZone#getDefault()})
	 * @since 4.3.5
	 * @see #getTimeZone()
	 * @see TimeZone#getDefault()
	 */
	public static void setDefaultTimeZone(@Nullable TimeZone timeZone) {
		defaultTimeZone = timeZone;
	}

	/**
	 * 返回与当前线程关联的时区（如果有），否则返回系统默认时区。
	 * Return the TimeZone associated with the current thread, if any,
	 * or the system default TimeZone otherwise. This is effectively a
	 * replacement for {@link java.util.TimeZone#getDefault()},
	 * able to optionally respect a user-level TimeZone setting.
	 * <p>Note: This method has a fallback to the shared default TimeZone,
	 * either at the framework level or at the JVM-wide system level.
	 * If you'd like to check for the raw LocaleContext content
	 * (which may indicate no specific time zone through {@code null}, use
	 * {@link #getLocaleContext()} and call {@link TimeZoneAwareLocaleContext#getTimeZone()}
	 * after downcasting to {@link TimeZoneAwareLocaleContext}.
	 * @return the current TimeZone, or the system default TimeZone if no
	 * specific TimeZone has been associated with the current thread
	 * @see #getLocaleContext()
	 * @see TimeZoneAwareLocaleContext#getTimeZone()
	 * @see #setDefaultTimeZone(TimeZone)
	 * @see java.util.TimeZone#getDefault()
	 */
	public static TimeZone getTimeZone() {
		return getTimeZone(getLocaleContext());
	}

	/**
	 * 返回与给定用户上下文（如果有）关联的时区，或系统默认时区。
	 * Return the TimeZone associated with the given user context, if any,
	 * or the system default TimeZone otherwise. This is effectively a
	 * replacement for {@link java.util.TimeZone#getDefault()},
	 * able to optionally respect a user-level TimeZone setting.
	 * @param localeContext the user-level locale context to check
	 * @return the current TimeZone, or the system default TimeZone if no
	 * specific TimeZone has been associated with the current thread
	 * @since 5.0
	 * @see #getTimeZone()
	 * @see TimeZoneAwareLocaleContext#getTimeZone()
	 * @see #setDefaultTimeZone(TimeZone)
	 * @see java.util.TimeZone#getDefault()
	 */
	public static TimeZone getTimeZone(@Nullable LocaleContext localeContext) {
		//如果可以解析时区信息
		if (localeContext instanceof TimeZoneAwareLocaleContext) {
			//从给定的上下文中进行解析
			TimeZone timeZone = ((TimeZoneAwareLocaleContext) localeContext).getTimeZone();
			if (timeZone != null) {
				//返回解析到的时区信息
				return timeZone;
			}
		}
		//返回框架级别的时区信息，如果没有指定则返回默认的时区信息（可能来自于启动参数，os等）
		return (defaultTimeZone != null ? defaultTimeZone : TimeZone.getDefault());
	}

}
