package io.ticlext;

import Atom.Utility.Random;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;

public class FreeProxyListNet implements ProxyProvider, Serializable {
    public static final URL base = Main.safeURL("https://free-proxy-list.net/");
    public static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    protected transient ArrayList<Proxy> proxies = new ArrayList<>();
    protected ArrayList<FreeProxyItem> items = new ArrayList<>();
    
    public FreeProxyListNet() throws IOException {
        items = scrap();
        for (FreeProxyItem item : items) {
            if (item.https){
                proxies.add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(item.ip, item.port)));
            }
        }
        if (proxies.size() == 0){
            throw new IOException("No proxies found");
        }
        System.out.println("Loaded " + proxies.size() + " proxies from FreeProxyListNet");
        gson.toJson(this, new FileWriter("proxies.json"));
    }
    
    public static ArrayList<FreeProxyItem> scrap() throws IOException {
        Document doc = Jsoup.parse(Main.getHTTP(base, true));
        ArrayList<FreeProxyItem> proxies = new ArrayList<>();
        for (Element e : doc.getElementsByTag("tbody").get(0).children()) {
            Elements ee = e.getElementsByTag("td");
            if (ee.size() == 8){
                FreeProxyItem item = new FreeProxyItem();
                item.ip = ee.get(0).text();
                item.port = Integer.parseInt(ee.get(1).text());
                item.countryCode = ee.get(2).text();
                item.country = ee.get(3).text();
                item.elite = ee.get(4).text().equals("elite proxy");
                item.google = ee.get(5).text().equals("yes");
                item.https = ee.get(6).text().equals("yes");
                proxies.add(item);
            }else{
                throw new IOException("Invalid proxy list column count: " + ee.size());
            }
        }
        return proxies;
    }
    
    @Override
    public Proxy get() {
        return Random.getRandom(proxies);
    }
    
    public static class FreeProxyItem implements Serializable {
        public String ip;
        public int port;
        public String country, countryCode;
        public boolean elite;
        public boolean google;
        public boolean https;
    }
}
