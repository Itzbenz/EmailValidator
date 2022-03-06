package io.ticlext;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public abstract class ScrapData<T> implements Serializable {
    public static transient Gson gson = new Gson();
    public URL nextURL;
    public List<T> places = new ArrayList<>();
    public int maxPage, page;
    
    public void save(File saveFile) throws IOException {
        gson.toJson(this, new FileWriter(saveFile));
    }
}
