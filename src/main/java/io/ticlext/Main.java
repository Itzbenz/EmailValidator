package io.ticlext;

import Atom.Time.Timer;
import Atom.Utility.Pool;
import io.ticlext.hotel.HotelContinentScrapper;
import io.ticlext.restaurant.RestaurantContinentScrapper;
import io.ticlext.restaurant.RestaurantPlace;
import io.ticlext.restaurant.RestaurantPlaceScrapData;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class Main {
    public static final Pattern emailRegex = Pattern.compile(
            "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?");
    static URL restaurantsURL = safeURL("https://www.tripadvisor.com/Restaurants-g4-Europe.html"), hotelsURL = safeURL(
            "https://www.tripadvisor.com/Hotels-g4-Europe-Hotels.html");
    static final Properties headers = new Properties();
    static File saveFile = new File("ScrapDataCheckpoint.json");
    public static String baseURL = "https://www.tripadvisor.com";
    static File headerFile = new File("header.properties");
    
    public static URL safeURL(String url) {
        try {
            return new URL(url);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
    
    static {
        headers.setProperty("User-Agent",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/98.0.4758.80 Safari/537.36");
        headers.setProperty("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        headers.setProperty("Accept-Language", "en-US,en;q=0.9");
        headers.setProperty("Upgrade-Insecure-Requests", "1");
    }
    
    static final Map<String, PrintStream> writers = Collections.synchronizedMap(new HashMap<>());
    public static void saveHeader(File headerFile) {
        try(FileOutputStream fos = new FileOutputStream(headerFile)) {
            headers.store(fos, "Header");
        }catch(IOException e){
            System.err.println("Error saving header file");
        }
    }
    
    public static void readHeader(File headerFile) {
        
        try(FileInputStream fis = new FileInputStream(headerFile)) {
            headers.clear();
            headers.load(fis);
        }catch(IOException e){
            System.err.println("Error reading header file");
        }
        headers.remove("accept-encoding");
    }
    
    static Timer saveTime = new Timer(TimeUnit.SECONDS, 10);
    
    public static void write(String country, String email) {
        if (email == null) return;
        if (email.isEmpty()) return;
        if (!emailRegex.matcher(email).matches()) return;
        if (!writers.containsKey(country)){
            try {
                writers.put(country, new PrintStream(new FileOutputStream(country + ".txt"), true));
            }catch(FileNotFoundException e){
                System.err.println("Error opening file: " + country + ".txt");
                return;
            }
        }
        writers.get(country).println(email);
    }
    
    public static void main(String[] args) throws Throwable {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        boolean first = true;
        if (saveFile.exists()){
            while (true) {
                System.out.print("Checkpoint (" + saveFile + ") found, continue from there? (y/n)");
                String ans = br.readLine();
                if (ans.equals("y")){
                    first = false;
                    break;
                }else if (ans.equals("n")){
                    break;
                }else{
                    System.out.println("Invalid input");
                }
            }
        }
        while (first) {
            try {
                System.out.print("Enter Restaurant URL to scrap [" + restaurantsURL + "]: ");
                String url = br.readLine();
                if (url.length() > 0){
                    restaurantsURL = new URL(url);
                }
                break;
            }catch(MalformedURLException e){
                System.err.println("Invalid URL");
            }catch(IOException e){
                System.err.println("Error: " + e.getMessage());
            }
        }
        while (first) {
            try {
                System.out.print("Enter Hotels URL to scrap [" + restaurantsURL + "]: ");
                String url = br.readLine();
                if (url.length() > 0){
                    hotelsURL = new URL(url);
                }
                break;
            }catch(MalformedURLException e){
                System.err.println("Invalid URL");
            }catch(IOException e){
                System.err.println("Error: " + e.getMessage());
            }
        }
        
        if (headerFile.exists()){
            readHeader(headerFile);
        }
        //set thread
        int thread = Math.max((Runtime.getRuntime().availableProcessors()) * 3, 1);
        Pool.parallelSupplier = () -> Executors.newFixedThreadPool(thread, (r) -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setName(t.getName() + "-Atomic-Executor");
            t.setDaemon(true);
            return t;
        });
        Pool.parallelAsync = Pool.parallelSupplier.get();
        System.out.println("Using " + thread + " threads");
        //set writers
    
    
        Thread hotelScrapper = null, restaurantScrapper = null;
        if (first){
            System.out.println("Scrapping hotels");
            HotelContinentScrapper regionScrapper = new HotelContinentScrapper(hotelsURL, hr -> {
                hr.getHotels()
                        .stream().filter(hotel -> hotel.email != null && hotel.email.length() > 0).forEach(hotel -> {
                            String country = hotel.country;
                            String email = hotel.email;
                            write(country, email);
                        });
            });
            hotelScrapper = regionScrapper.init();
            hotelScrapper.join();
        }
        if (first){
            System.out.println("Scrapping restaurants");
            RestaurantContinentScrapper regionScrapper = new RestaurantContinentScrapper(restaurantsURL, hr -> {
                hr.getRestaurants()
                        .stream()
                        .filter(restaurant -> restaurant.email != null && restaurant.email.length() > 0)
                        .forEach(restaurant -> {
                            String country = restaurant.country;
                            String email = restaurant.email;
                            write(country, email);
                        });
            });
            restaurantScrapper = regionScrapper.init();
            restaurantScrapper.join();
        }
    }
    
    public static void scrapResto(RestaurantPlaceScrapData sc) throws IOException {
        //System.out.println("Scrapping " + sc.page + "/" + sc.maxPage + " pages");
        Document doc = Jsoup.parse(getHTTP(sc.nextURL));
        for (Element pl : doc.select("ul.geoList").get(0).children()) {
            pl = pl.child(0);
            RestaurantPlace p = new RestaurantPlace();
            p.name = pl.text();
            p.url = new URL(baseURL + pl.attr("href"));
            sc.places.add(p);
        }
        
        Elements pageLink = doc.select("div.pgLinks");
        try {
            sc.nextURL = new URL(baseURL + pageLink.select("a.guiArw.sprite-pageNext").attr("href"));
        }catch(Exception ignored){}
        try {
            sc.maxPage = Integer.parseInt(pageLink.select("a.paging.taLnk").last().text());
        }catch(Exception ignored){}
        sc.page++;
    }
    
    public static String getHTTP(URL url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        for (Object o : headers.keySet()) {
            String key = String.valueOf(o);
            con.setRequestProperty(key, headers.getProperty(key));
        }
        con.setRequestMethod("GET");
        con.setDoOutput(true);
        con.setDoInput(true);
        con.connect();
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        if (con.getHeaderField("Set-Cookie") != null){
            headers.setProperty("Cookie", con.getHeaderField("Set-Cookie"));
            saveHeader(headerFile);
        }
        return sb.toString();
    }
    
    public static RestaurantPlaceScrapData scrapFirstPage() throws Exception {
        Document doc = Jsoup.parse(getHTTP(restaurantsURL));
        URL nextPage = new URL(baseURL + doc.select("a.nav.next.rndBtn.ui_button.primary.taLnk").attr("href"));
        RestaurantPlaceScrapData scrapData = new RestaurantPlaceScrapData();
        scrapData.nextURL = nextPage;
        for (Element d : doc.select("div.geo_name")) {
            RestaurantPlace place = new RestaurantPlace();
            place.name = d.text();
            place.url = new URL(baseURL + d.select("a").attr("href"));
            scrapData.places.add(place);
        }
        scrapData.page = 1;
        return scrapData;
    }
}
