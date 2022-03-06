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
    
    static void testProxy(Proxy proxyProvider, int timeout) throws IOException {
        //"https://ip-api.com/line?fields=status,proxy,hosting,query"
        String url = Main.baseURL;
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection(proxyProvider);
    
        h.setConnectTimeout(timeout);
        h.setReadTimeout(timeout);
        h.connect();
        int code = h.getResponseCode();
        if (code != 200){
            throw new IOException("Proxy failed: " + code);
        }
    }
}
