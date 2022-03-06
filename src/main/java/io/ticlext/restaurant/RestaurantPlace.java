package io.ticlext.restaurant;

import Atom.Reflect.UnThread;
import Atom.Utility.Pool;
import io.ticlext.Main;
import io.ticlext.ScrapData;
import me.tongfei.progressbar.ProgressBar;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.regex.Matcher;

public class RestaurantPlace implements Serializable {
    
    public String name;
    public URL url;
    private transient Future<String> htmlCache;
    private transient List<Future<String>> futures = new ArrayList<>();
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RestaurantPlace place = (RestaurantPlace) o;
        return Objects.equals(name, place.name) && Objects.equals(url, place.url);
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
            /*
            try{
                if(e.parent().children().get(0).attr("class").contains(" phone ")){
                    restaurant.phone = e.text().trim();
                }
            }catch(Exception ignored){}
            
             */
            
        }
        restaurant.country = restaurant.address.split(" ")[restaurant.address.split(" ").length - 1];
        return restaurant;
    }
    
    public void scrap(RestaurantScrapData data) throws IOException {
        if (htmlCache == null) htmlCache = Pool.submit(() -> Main.getHTTP(data.nextURL));
        String html = "";
        try {
            html = htmlCache.get();
        }catch(InterruptedException | ExecutionException e){
            throw new IOException(e);
        }
        Document doc = Jsoup.parse(html);
        try {
            data.nextURL = new URL(Main.baseURL + doc.select("a.nav.next.rndBtn.ui_button.primary.taLnk")
                    .get(0)
                    .attr("href"));
        }catch(Exception ignored){
            data.nextURL = null;
        }
        if (data.nextURL != null){
            htmlCache = Pool.submit(() -> Main.getHTTP(data.nextURL));
        }
        
        for (Element e : doc.getElementsByClass("bHGqj Cj b")) {
            URL url = new URL(Main.baseURL + e.attr("href"));
            futures.add(Pool.submit(() -> Main.getHTTP(url)));
            
        }
        
        try {
            data.maxPage = Integer.parseInt(doc.getElementsByClass("pageNum taLnk").last().text().trim());
            data.page++;
        }catch(Exception ignored){}
        
    }
    
    private void processFuture(RestaurantScrapData data, ProgressBar pb, boolean wait) {
        
        while (futures.size() > 0) {
            pb.maxHint(pb.getCurrent() + futures.size());
            pb.setExtraMessage("places");
            for (Future<String> f : new ArrayList<>(futures)) {
                if (f.isDone()){
                    try {
                        String sex = f.get();
                        Document doc2 = Jsoup.parse(sex);
                        Restaurant restaurant = scrap(doc2);
                        if (restaurant.email != null && !restaurant.email.isEmpty()){
                            data.places.add(restaurant);
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
    
    public void scrap(Consumer<Restaurant> onData) {
        RestaurantScrapData data = new RestaurantScrapData();
        data.nextURL = url;
        data.page = 1;
        try(ProgressBar pb = new ProgressBar("Scrapping: " + name, -1)) {
            while (data.nextURL != null) {
                try {
                    //pb.maxHint(data.maxPage);
                    //pb.stepTo(data.page);
                    //pb.setExtraMessage("pages");
                    scrap(data);
                    if (htmlCache != null){
                        while (!htmlCache.isDone()) {
                            processFuture(data, pb, false);
                            if (data.places.size() > 0){
                                for (Restaurant t : data.places) {
                                    onData.accept(t);
                                }
                                data.places.clear();
                            }
                        }
                    }
                    
                }catch(Exception e){
                    pb.setExtraMessage(e.getMessage());
                }
            }
            processFuture(data, pb, true);
            if (data.places.size() > 0){
                for (Restaurant t : data.places) {
                    onData.accept(t);
                }
                data.places.clear();
            }
        }
        
        
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, url);
    }
    
    public static class Restaurant implements Serializable {
        public String email = "", address = "", country = "";
        
        
    }
    
    public static class RestaurantScrapData extends ScrapData<Restaurant> {
        public static RestaurantScrapData load(File saveFile) throws FileNotFoundException {
            return gson.fromJson(new FileReader(saveFile), RestaurantScrapData.class);
        }
    }
}
