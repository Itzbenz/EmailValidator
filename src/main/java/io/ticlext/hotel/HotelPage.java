package io.ticlext.hotel;

import Atom.Reflect.UnThread;
import Atom.Utility.Pool;
import io.ticlext.Main;
import io.ticlext.ScrapData;
import me.tongfei.progressbar.ProgressBar;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import static io.ticlext.Main.emailRegex;

public class HotelPage extends ScrapData<Hotel> {
    private final transient List<Future<String>> futures = new ArrayList<>();
    private transient Future<String> htmlCache = null;
    
    public static boolean hotelSatisfyNeed(Element e) {
        boolean need = false;
        try {
            for (Element feat : e.parent().parent().getElementsByClass("text_container")) {
                if (feat.text().toLowerCase().trim().contains("restaurant")){
                    need = true;
                    break;
                }
            }
        }catch(Exception ignored){}
        return need;
    }
    
    public static HotelPage scrap(URL url) throws IOException {
        HotelPage hotelPage = new HotelPage();
        scrap(url, hotelPage);
        return hotelPage;
    }
    
    private static void addItems(Document page, HotelPage hotelPage) throws MalformedURLException {
        
        for (Element e : page.getElementsByClass("listing_title")) {
            if (!hotelSatisfyNeed(e)) continue;
            URL url2 = new URL(Main.baseURL + e.select("a").get(0).attr("href"));
            hotelPage.futures.add(Pool.submit(() -> Main.getHTTP(url2)));
            
        }
    }
    
    public static void scrap(URL url, HotelPage page) throws IOException {
        
        Document doc = Jsoup.parse(Main.getHTTP(url));
        page.nextURL = new URL(Main.baseURL + doc.select("a.nav.next.ui_button.primary").get(0).attr("href"));
        page.htmlCache = Pool.submit(() -> Main.getHTTP(url));
        List<Future<String>> futures = new ArrayList<>();
        for (Element e : doc.getElementsByClass("listing_title")) {
            if (!hotelSatisfyNeed(e)) continue;
            URL url2 = new URL(Main.baseURL + e.select("a").get(0).attr("href"));
            futures.add(Pool.submit(() -> Main.getHTTP(url2)));
            
        }
        
        for (Future<String> f : ProgressBar.wrap(futures, "hotels")) {
            try {
                String html = f.get();
                Document doc2 = Jsoup.parse(html);
                Hotel h = page.scrap(doc2);
                if (h.email != null && !h.email.isEmpty()){
                    page.places.add(h);
                }
            }catch(InterruptedException | ExecutionException ignored){
            
            }
        }
        
    }
    
    public void scrap() {
    
    }
    
    private void processFuture(ProgressBar pb, boolean wait) {
        while (futures.size() > 0) {
            pb.maxHint(pb.getCurrent() + futures.size());
            pb.setExtraMessage("hotels");
            for (Future<String> f : new ArrayList<>(futures)) {
                if (f.isDone()){
                    try {
                        String sex = f.get();
                        Document doc2 = Jsoup.parse(sex);
                        Hotel hotel = scrap(doc2);
                        if (hotel.email != null && !hotel.email.isEmpty()){
                            places.add(hotel);
                        }
                    }catch(InterruptedException | ExecutionException e){
                        pb.setExtraMessage(e.getMessage());
                        //System.err.println("Error processing restaurant: " + e.getMessage());
                    }
                    futures.remove(f);
                    pb.step();
                }
            }
            UnThread.sleep(250);
            if (!wait) break;
        }
    }
    
    private Hotel scrap(Document doc2) {
        Hotel hotel = new Hotel();
        hotel.name = doc2.getElementsByClass("header heading masthead masthead_h1 ").get(0).text();
        try {
            hotel.email = doc2.getElementsByClass("bIWzQ fWKZw").get(0).attr("href");
            Matcher match = emailRegex.matcher(hotel.email);
            if (match.find()){
                hotel.email = match.group();
            }
        }catch(Exception ignored){}
        hotel.country = doc2.getElementsByClass("breadcrumbs").get(0).children().get(1).text();
        return hotel;
    }
    
    private void getNextPage() throws ExecutionException, InterruptedException {
        if (htmlCache == null) htmlCache = Pool.submit(() -> Main.getHTTP(nextURL));
        String html = htmlCache.get();
        Document doc = Jsoup.parse(html);
        try {
            nextURL = new URL(Main.baseURL + doc.select("a.nav.next.ui_button.primary").get(0).attr("href"));
        }catch(Exception ignored){
            nextURL = null;
        }
        if (nextURL != null){
            htmlCache = Pool.submit(() -> Main.getHTTP(nextURL));
        }
        
        
        System.out.println(doc);
    }
    
    public void scrapContinuous(Consumer<Hotel> consumer) {
        try(ProgressBar pb = new ProgressBar("Scraping Hotel", -1)) {
            while (nextURL != null) {
                if (htmlCache != null){
                    while (!htmlCache.isDone()) {
                        processFuture(pb, false);
                        while (!places.isEmpty()) {
                            consumer.accept(places.remove(0));
                        }
                    }
                }
                try {
                    getNextPage();
                }catch(ExecutionException | InterruptedException e){
                    pb.setExtraMessage(e.getMessage());
                }
            }
            processFuture(pb, true);
        }
    }
    
    
}
