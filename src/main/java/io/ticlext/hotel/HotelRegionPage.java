package io.ticlext.hotel;

import Atom.Reflect.UnThread;
import Atom.Utility.Random;
import io.ticlext.Main;
import io.ticlext.Scrapper;
import me.tongfei.progressbar.ProgressBar;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import static io.ticlext.Main.emailRegex;

public class HotelRegionPage extends Scrapper<String> {
    static {
    
    }
    
    public static void desc() {
        System.out.println("Scrapping Hotel that only contain restaurant");
    }
    
    protected String name;
    protected Consumer<Hotel> onData;
    
    public HotelRegionPage(String name, URL url, Consumer<Hotel> onData) {
        this.name = name;
        setNextURL(url);
        this.onData = onData;
    }
    
    
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
    
    protected void onFuture(String html) {
        try {
            if (html == null || html.isEmpty()) return;
            Document doc2 = Jsoup.parse(html);
            Hotel hotel = new Hotel();
            hotel.name = doc2.getElementsByClass("header heading masthead masthead_h1 ").get(0).text();
            try {
                hotel.email = doc2.getElementsByClass("bIWzQ fWKZw").get(0).attr("href");
                Matcher match = emailRegex.matcher(hotel.email);
                if (match.find()){
                    hotel.email = match.group();
                }
            }catch(Exception ignored){}
            hotel.country = doc2.getElementsByClass("breadcrumbs").get(0).children().get(Main.sortBy.ordinal()).text();
            onData.accept(hotel);
        }catch(Exception e){
            e.printStackTrace();
            UnThread.sleep(10000);//fix bug on runtime speedrun
        }
        
    }
    
    URL scrapNextURL(Document doc) {
        try {
            return new URL(Main.baseURL + doc.getElementsByClass("nav next ui_button primary").get(0).attr("href"));
        }catch(MalformedURLException e){
            return null;
        }
    }
    
    //filter hotel that contain restaurant and process it
    void scrapItems(Document doc) {
        
        for (Element e : doc.getElementsByClass("listing_title")) {
            if (!hotelSatisfyNeed(e)){
                process(() -> {
                    UnThread.sleep(Random.getInt(120, 8000));
                    return "";
                });
                continue;
            }
            ;
            try {
                URL url2 = new URL(Main.baseURL + e.select("a").get(0).attr("href"));
                process(() -> Main.getHTTP(url2));
            }catch(MalformedURLException ignored){
        
            }
        }
    }
    
    void scrapPage() throws IOException {
        Document doc = Jsoup.parse(getHTMLNext());
        setNextURL(scrapNextURL(doc));
        scrapItems(doc);
    }
    
    @Override
    public void run() {
        try(ProgressBar pb = new ProgressBar("Scrapping: " + name, -1)) {
            while (nextURL != null) {
                try {
                    scrapPage();
                    pb.setExtraMessage("hotels");
                    waitForNextPage(pb);
                }catch(Exception e){
                    pb.setExtraMessage(e.getMessage());
                }
            }
            processFuture(pb, true);
        }
    }
    
    @Override
    protected boolean nestedThreading() {
        return false;
    }
}
