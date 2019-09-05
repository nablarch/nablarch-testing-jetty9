package nablarch.fw.web.httpserver;

import nablarch.fw.web.HttpServer;
import nablarch.fw.web.HttpServerFactory;

/**
 * Jetty9対応の{@link HttpServer}を生成するファクトリ実装クラス。
 *
 * @author Taichi Uragami
 */
public class HttpServerFactoryJetty9 implements HttpServerFactory {

    @Override
    public HttpServer create() {
        return new HttpServerJetty9();
    }
}
