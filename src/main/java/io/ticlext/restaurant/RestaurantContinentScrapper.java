package io.ticlext.restaurant;

import io.ticlext.Main;
import io.ticlext.Scrapper;
import me.tongfei.progressbar.ProgressBar;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;

public class RestaurantContinentScrapper extends Scrapper<RestaurantRegionPage> {
    protected boolean first = true;
    protected transient Consumer<RestaurantRegionPage> onData;
    
    public RestaurantContinentScrapper(URL url, Consumer<RestaurantRegionPage> onData) {
        nextURL = url;
        this.onData = onData;
    }
    
    public RestaurantContinentScrapper setOnData(Consumer<RestaurantRegionPage> onData) {
        this.onData = onData;
        return this;
    }
    
    protected void firstTime() throws IOException {
        if (!first) return;
        first = false;
        Document doc = Jsoup.parse(getHTMLNext());
        URL nextPage = new URL(Main.baseURL + doc.select("a.nav.next.rndBtn.ui_button.primary.taLnk").attr("href"));
        setNextURL(nextPage);
        for (Element d : doc.select("div.geo_name")) {
            RestaurantRegionPage r = new RestaurantRegionPage(d.text().trim(),
                    new URL(Main.baseURL + d.select("a").attr("href")));
            process(r);
        }
    }
    
    @Override
    protected void onFuture(RestaurantRegionPage restaurantRegionPage) {
        onData.accept(restaurantRegionPage);
    }
    
    protected void process(final RestaurantRegionPage r) {
        
        process(() -> {
            r.init();
            return r;
        });
        
    }
    
    protected URL getNextURL(Document doc) {
        
        try {
            Elements pageLink = doc.select("div.pgLinks");
            return new URL(Main.baseURL + pageLink.select("a.guiArw.sprite-pageNext").attr("href"));
        }catch(Exception e){
            return null;
        }
    }
    
    protected void scrapItems(Document doc) {
        for (Element pl : doc.select("ul.geoList").get(0).children()) {
            try {
                pl = pl.child(0);
                RestaurantRegionPage r = new RestaurantRegionPage(pl.text().trim(),
                        new URL(Main.baseURL + pl.attr("href")));
                process(r);
            }catch(Exception ignored){
            
            }
        }
    }
    
    protected void scrapPage() throws IOException {
        Document doc = Jsoup.parse(getHTMLNext());
        setNextURL(getNextURL(doc));
        scrapItems(doc);
    }
    
    @Override
    public void run() {
        if (!first) throw new IllegalStateException("Invalid state, firstRun() must be called before run()");
        try(ProgressBar pb = new ProgressBar("Scrapping Restaurants", -1)) {
            while (nextURL != null) {
                try {
                    scrapPage();
                    pb.setExtraMessage("restaurants");
                    processFuture(pb, true);
                }catch(Exception e){
                    pb.setExtraMessage(e.getMessage());
                }
            }
            processFuture(pb, true);
        }
    }
}
