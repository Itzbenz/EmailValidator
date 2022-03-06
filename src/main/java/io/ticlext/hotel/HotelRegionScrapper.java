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

public class HotelRegionScrapper extends Scrapper<Void> {
    protected boolean first = true;
    
    public HotelRegionScrapper(URL baseURL) throws IOException {
        nextURL = baseURL;
        firstRun();
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
                HotelRegionPage item = new HotelRegionPage(name, url);
                items.add(item);
            }catch(MalformedURLException ignored){
            
            }
        }
        return items;
    }
    
    
    List<HotelRegionPage> scrapPage() throws IOException {
        Document doc = Jsoup.parse(getHTMLNext());
        setNextURL(scrapNextURL(doc));
        return scrapPageItems(doc);
    }
    
    void processPage(List<HotelRegionPage> items) {
        for (HotelRegionPage item : items) {
            process(() -> {
                item.init();
                return null;
            });
        }
    }
    
    
    @Override
    public void run() {
        if (!first) throw new IllegalStateException("Invalid state, firstRun() must be called before run()");
        try(ProgressBar pb = new ProgressBar(this.getClass().getSimpleName(), -1)) {
            while (nextURL != null) {
                try {
                    List<HotelRegionPage> items = scrapPage();
                    processPage(items);
                    pb.setExtraMessage("Region");
                    processFuture(pb, true);
                }catch(Exception e){
                    pb.setExtraMessage(e.getMessage());
                }
            }
        }
    }
}
