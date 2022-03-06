package io.ticlext;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.function.Supplier;

public interface ProxyProvider extends Supplier<Proxy> {
    static void testProxy(Proxy proxyProvider) throws IOException {
        testProxy(proxyProvider, 20 * 1000);
    }
    
    default void update() {
    
    }
    
    static void testProxy(Proxy proxyProvider, int timeout) throws IOException {
        //"https://ip-api.com/line?fields=status,proxy,hosting,query"
        String url = Main.baseURL;
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection(proxyProvider);
        Main.headersSupplier.get().forEach((k, v) -> {
            String key = String.valueOf(k);
            String value = String.valueOf(v);
            h.setRequestProperty(key, value);
        });
        h.setConnectTimeout(timeout);
        h.setReadTimeout(timeout);
        h.connect();
        int code = h.getResponseCode();
        if (code != 200){
            throw new IOException("Proxy failed: " + code);
        }
    }
}
