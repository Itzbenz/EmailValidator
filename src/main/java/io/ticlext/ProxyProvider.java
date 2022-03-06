package io.ticlext;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.function.Supplier;

public interface ProxyProvider extends Supplier<Proxy> {
    static void testProxy(Proxy proxyProvider) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL("https://ip-api.com/line?fields=status,proxy,hosting,query").openConnection(
                proxyProvider);
        h.setConnectTimeout(5000);
        h.setReadTimeout(5000);
        h.connect();
        int code = h.getResponseCode();
        if (code != 200){
            throw new IOException("Proxy failed: " + code);
        }
    }
}
