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
    protected transient Consumer<Restaurant> onData;
    protected int maxLocation = 1;
    
    public RestaurantContinentScrapper(URL url, Consumer<Restaurant> onData) {
        nextURL = url;
        this.onData = onData;
        
    }
    
    public RestaurantContinentScrapper setOnData(Consumer<Restaurant> onData) {
        this.onData = onData;
        return this;
    }
    
    protected void scrapMetadata(Document doc) {
        try {
            maxLocation = Integer.parseInt(doc.getElementsByClass("pgCount").get(0).text().split("of ")[1].replace(",",
                    ""));
        }catch(Exception e){
        
        }
    }
    
    protected void firstTime() throws IOException {
        if (!first) return;
        first = false;
        Document doc = Jsoup.parse(getHTMLNext());
        URL nextPage = new URL(Main.baseURL + doc.select("a.nav.next.rndBtn.ui_button.primary.taLnk").attr("href"));
        setNextURL(nextPage);
        for (Element d : doc.select("div.geo_name")) {
            RestaurantRegionPage r = new RestaurantRegionPage(d.text().trim(),
                    new URL(Main.baseURL + d.select("a").attr("href")),
                    onData);
            process(r);
        }
    }
    
    @Override
    protected void onFuture(RestaurantRegionPage restaurantRegionPage) {
    
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
                        new URL(Main.baseURL + pl.attr("href")),
                        onData);
                process(r);
            }catch(Exception ignored){
    
            }
        }
    }
    
    protected void scrapPage() throws IOException {
        Document doc = Jsoup.parse(getHTMLNext());
        setNextURL(getNextURL(doc));
        scrapItems(doc);
        scrapMetadata(doc);
    }
    
    @Override
    protected long getMaxHint(ProgressBar pb) {
        if (maxLocation == -1 || pb.getCurrent() > maxLocation){
            return super.getMaxHint(pb);
        }
        return maxLocation;
    }
    
    @Override
    public void run() {
        if (first){
            try {
                firstTime();
            }catch(IOException e){
                e.printStackTrace();
                return;
            }
        }
        try(ProgressBar pb = new ProgressBar(getClass().getSimpleName(), maxLocation)) {
            while (nextURL != null) {
                try {
                    scrapPage();
                    pb.setExtraMessage("restaurants");
                    processFuture(pb, true);
                }catch(Exception e){
                    pb.setExtraMessage(e.getMessage());
                    Main.handleException(e);
                }
            }
            processFuture(pb, true);
        }
    }
}
