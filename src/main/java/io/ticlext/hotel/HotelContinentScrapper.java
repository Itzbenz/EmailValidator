package io.ticlext.hotel;

import io.ticlext.Main;
import io.ticlext.Scrapper;
import me.tongfei.progressbar.ProgressBar;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HotelContinentScrapper extends Scrapper<HotelRegionPage> {
    protected boolean first = true;
    protected transient Consumer<Hotel> onData;
    
    public HotelContinentScrapper(URL baseURL, Consumer<Hotel> onData) {
        nextURL = baseURL;
        this.onData = onData;
        
    }
    
    public HotelContinentScrapper setOnData(Consumer<Hotel> onData) {
        this.onData = onData;
        return this;
    }
    
    void firstRun() throws IOException {
        if (!first) return;
        first = false;
        Document doc = Jsoup.parse(Main.getHTTP(nextURL));
        nextURL = new URL(Main.baseURL + doc.select("a.nav.next.ui_button.primary").get(0).attr("href"));
        setNextURL(nextURL);
    }
    
    URL scrapNextURL(Document doc) {
        try {
            return new URL(Main.baseURL + doc.select("a.nav.next.ui_button.primary").get(0).attr("href"));
        }catch(Exception e){
            return null;
        }
    }
    
    List<HotelRegionPage> scrapPageItems(Document doc) {
        ArrayList<HotelRegionPage> items = new ArrayList<>();
        for (Element e : doc.getElementsByClass("city")) {
            try {
                URL url = new URL(Main.baseURL + e.attr("href"));
                String name = e.getElementsByClass("name").get(0).text();
                HotelRegionPage item = new HotelRegionPage(name, url, onData);
                items.add(item);
            }catch(MalformedURLException ignored){
    
            }
        }
        return items;
    }
    
    
    void scrapPage() throws IOException {
        Document doc = Jsoup.parse(getHTMLNext());
        setNextURL(scrapNextURL(doc));
        processPage(scrapPageItems(doc));
    }
    
    @Override
    protected void onFuture(HotelRegionPage hotelRegionPage) {
    
    }
    
    void processPage(final List<HotelRegionPage> items) {
        for (final HotelRegionPage item : items) {
            process(() -> {
                item.init();
                return item;
            });
        }
    }
    
    
    @Override
    public void run() {
        if (first){
            try {
                firstRun();
            }catch(IOException e){
                e.printStackTrace();
                return;
            }
        }
        try(ProgressBar pb = new ProgressBar(this.getClass().getSimpleName(), -1)) {
            while (nextURL != null) {
                try {
                    scrapPage();
                    pb.setExtraMessage("region");
                    processFuture(pb, true);
                }catch(Exception e){
                    pb.setExtraMessage(e.getMessage());
                }
            }
        }
    }
}
