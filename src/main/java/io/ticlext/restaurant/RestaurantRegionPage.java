package io.ticlext.restaurant;

import io.ticlext.Main;
import io.ticlext.Scrapper;
import me.tongfei.progressbar.ProgressBar;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class RestaurantRegionPage extends Scrapper<String> {
    protected String name;
    protected List<Restaurant> restaurants = new ArrayList<>();
    
    public RestaurantRegionPage(String regionName, URL url) {
        setNextURL(url);
        this.name = regionName;
    }
    
    public List<Restaurant> getRestaurants() {
        return restaurants;
    }
    
    public Restaurant scrap(Document doc) {
        Restaurant restaurant = new Restaurant();
        Elements elements = doc.getElementsByClass("brMTW");
        //restaurant.name = doc.getElementsByClass("fHibz").last().text().trim();
        restaurant.address = elements.get(0).text().trim();
        for (Element e : elements) {
            if (e.text().equals("Email")){
                restaurant.email = e.parent().attr("href").trim();
                Matcher matcher = Main.emailRegex.matcher(restaurant.email);
                if (matcher.find()){
                    restaurant.email = matcher.group();
                }
            }
        }
        restaurant.country = restaurant.address.split(" ")[restaurant.address.split(" ").length - 1];
        return restaurant;
    }
    
    @Override
    protected void onFuture(String s) {
        Document doc = Jsoup.parse(s);
        try {
            restaurants.add(scrap(doc));
        }catch(Exception ignored){
        
        }
    }
    
    protected URL scrapNextURL(Document doc) {
        try {
            return new URL(Main.baseURL + doc.select("a.nav.next.rndBtn.ui_button.primary.taLnk").get(0).attr("href"));
        }catch(Exception e){
            return null;
        }
    }
    
    protected void scrapItems(Document doc) {
        for (Element e : doc.getElementsByClass("bHGqj Cj b")) {
            try {
                URL url = new URL(Main.baseURL + e.attr("href"));
                process(() -> Main.getHTTP(url));
            }catch(MalformedURLException ignored){
            
            }
        }
    }
    
    protected void scrapPage() throws IOException {
        Document doc = Jsoup.parse(getHTMLNext());
        setNextURL(scrapNextURL(doc));
        scrapItems(doc);
    }
    
    @Override
    public void run() {
        try(ProgressBar pb = new ProgressBar("Scraping: " + name, -1)) {
            while (nextURL != null) {
                try {
                    scrapPage();
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
