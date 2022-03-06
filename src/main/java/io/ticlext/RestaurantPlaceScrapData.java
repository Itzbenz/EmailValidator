package io.ticlext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

public class RestaurantPlaceScrapData extends ScrapData<RestaurantPlace> {
    public static RestaurantPlaceScrapData load(File saveFile) throws FileNotFoundException {
        return gson.fromJson(new FileReader(saveFile), RestaurantPlaceScrapData.class);
    }
}
