package nablarch.fw.web.httpserver;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Enumeration;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;

public class LazySessionInvalidationFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest wrappedRequest = RequestWrapper.wrap((HttpServletRequest) request);
        chain.doFilter(wrappedRequest, response);

        RequestWrapper wrapper = (RequestWrapper) Proxy.getInvocationHandler(wrappedRequest);
        if (wrapper.isInvalidated()) {
            wrapper.invalidateSessionActually();
        }
    }

    @Override
    public void destroy() {
    }

    private static class RequestWrapper implements InvocationHandler, Runnable {

        private final HttpServletRequest request;
        private boolean invalidated;

        public RequestWrapper(HttpServletRequest request) {
            this.request = request;
        }

        public void invalidateSessionActually() {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

            if (method.equals(HttpServletRequest.class.getDeclaredMethod("getSession"))
                    || method.equals(HttpServletRequest.class.getDeclaredMethod("getSession",
                            boolean.class))) {
                Object session = method.invoke(request, args);
                if (session == null) {
                    return null;
                }
                return SessionWrapper.wrap((HttpSession) session, this);
            }

            return method.invoke(request, args);
        }

        @Override
        public void run() {
            invalidated = true;
        }

        public boolean isInvalidated() {
            return invalidated;
        }

        public static HttpServletRequest wrap(HttpServletRequest request) {
            ClassLoader loader = request.getClass().getClassLoader();
            Class<?>[] interfaces = { HttpServletRequest.class };
            InvocationHandler h = new RequestWrapper(request);
            return (HttpServletRequest) Proxy.newProxyInstance(loader, interfaces, h);
        }
    }

    private static class SessionWrapper implements InvocationHandler {

        private HttpSession session;
        private Runnable invalidationCallback;

        public SessionWrapper(HttpSession session, Runnable invalidationCallback) {
            this.session = session;
            this.invalidationCallback = invalidationCallback;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

            if (method.equals(HttpSession.class.getDeclaredMethod("invalidate"))) {
                invalidationCallback.run();

                Enumeration<String> names = session.getAttributeNames();
                while (names.hasMoreElements()) {
                    String name = names.nextElement();
                    session.removeAttribute(name);
                }

                return null;
            }
            return method.invoke(session, args);
        }

        public static HttpSession wrap(HttpSession session, Runnable invalidationCallback) {
            ClassLoader loader = session.getClass().getClassLoader();
            Class<?>[] interfaces = { HttpSession.class };
            InvocationHandler h = new SessionWrapper(session, invalidationCallback);
            return (HttpSession) Proxy.newProxyInstance(loader, interfaces, h);
        }
    }
}
