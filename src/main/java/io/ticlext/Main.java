package io.ticlext;

import Atom.Time.Timer;
import Atom.Utility.Pool;
import me.tongfei.progressbar.ProgressBar;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Main {
    static final HashMap<String, String> headers = new HashMap<>();
    static URL restaurantsURL = safeURL("https://www.tripadvisor.com/Restaurants-g4-Europe.html"), hotelsURL = safeURL(
            "https://www.tripadvisor.com/Hotels-g4-Europe-Hotels.html");
    static String baseURL = "https://www.tripadvisor.com";
    static File saveFile = new File("ScrapDataCheckpoint.json");
    
    public static URL safeURL(String url) {
        try {
            return new URL(url);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
    
    public static void readHeader(String headerFile) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(headerFile)));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(":")){
                    continue;
                }
                String[] parts = line.split(":");
                headers.put(parts[0].toLowerCase(), parts[1]);
            }
        }catch(Exception e){
            throw new RuntimeException(e);
        }
        headers.remove("accept-encoding");
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
        File headerFile = new File("header.txt");
        if (headerFile.exists()){
            readHeader("header.txt");
        }
        
        Pool.parallelSupplier = () -> Executors.newFixedThreadPool(Math.max((Runtime.getRuntime()
                .availableProcessors()) * 3, 2), (r) -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setName(t.getName() + "-Atomic-Executor");
            t.setDaemon(true);
            return t;
        });
        Pool.parallelAsync = Pool.parallelSupplier.get();
        System.out.println("Using " + Math.max((Runtime.getRuntime().availableProcessors()) * 2, 2) + " threads");
        final Map<String, PrintStream> writers = Collections.synchronizedMap(new HashMap<>());
        RestaurantPlaceScrapData sc = null;
        
        
        Timer saveTime = new Timer(TimeUnit.SECONDS, 10);
        if (first && false){
            HotelPage.scrap(hotelsURL).scrapContinuous(hotel -> {
                writers.computeIfAbsent(hotel.country, (c) -> {
                    try {
                        File kike = new File(hotel.country + ".txt");
                        return new PrintStream(new FileOutputStream(kike));
                    }catch(IOException e){
                        throw new RuntimeException(e);
                    }
                });
                writers.get(hotel.country).println(hotel.email);
                writers.get(hotel.country).flush();
            });
        }
        if (!first){
            sc = RestaurantPlaceScrapData.load(saveFile);
        }else{
            sc = scrapFirstPage();
        }
        try(ProgressBar pb = new ProgressBar("Scraping Restaurant", -1)) {
            while (sc.nextURL != null) {
                //empty data
                if (!sc.places.isEmpty()){
                    try(ProgressBar pb2 = new ProgressBar("Scrapping Location", -1)) {
                        List<Future<?>> future = new ArrayList<>();
                        for (RestaurantPlace place : sc.places) {
                            future.add(Pool.async(() -> {
                                try {
                                    place.scrap(d -> {
                                        String country = d.country;
                                        writers.computeIfAbsent(country, (c) -> {
                                            try {
                                                File kike = new File(country + ".txt");
                                                return new PrintStream(new FileOutputStream(kike));
                                            }catch(IOException e){
                                                throw new RuntimeException(e);
                                            }
                                        });
                                        writers.get(country).println(d.email);
                                        writers.get(country).flush();
                                    });
                                }catch(Exception e){
                                    System.err.println("Error: " + e.getMessage());
                                }
                            }));
                        }
                        //
                        sc.places.clear();
                        try {
                            scrapResto(sc);
                            pb.setExtraMessage("pages");
                            pb.maxHint(sc.maxPage);
                            pb.stepTo(sc.page);
                        }catch(IOException e){
                            System.err.println("Error: " + e.getMessage());
                            break;
                        }
                        if (saveTime.get()){
                            try {
                                sc.save(saveFile);
                            }catch(IOException e){
                                System.err.println("Failed to save checkpoint: " + e.getMessage());
                            }
                        }
                        //
                        pb2.setExtraMessage("locations");
                        pb2.maxHint(future.size());
                        pb2.stepTo(0);
                        for (Future<?> f : future) {
                            pb2.step();
                            f.get();
                        }
                        
                    }
                }
                
                try {
                    scrapResto(sc);
                    pb.setExtraMessage("pages");
                    pb.maxHint(sc.maxPage);
                    pb.stepTo(sc.page);
                }catch(IOException e){
                    System.err.println("Error: " + e.getMessage());
                    break;
                }
                if (saveTime.get()){
                    try {
                        sc.save(saveFile);
                    }catch(IOException e){
                        System.err.println("Failed to save checkpoint: " + e.getMessage());
                    }
                }
                
            }
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
        for (String key : headers.keySet()) {
            con.setRequestProperty(key, headers.get(key));
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
