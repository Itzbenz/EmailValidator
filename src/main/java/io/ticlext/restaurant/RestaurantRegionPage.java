package io.ticlext.restaurant;

import io.ticlext.Scrapper;

import java.net.URL;

public class RestaurantRegionPage extends Scrapper<String> {
    protected String name;
    
    public RestaurantRegionPage(String regionName, URL url) {
        setNextURL(url);
        this.name = regionName;
    }
    
    @Override
    public void run() {
    
    }
    
    @Override
    protected boolean nestedThreading() {
        return false;
    }
}
